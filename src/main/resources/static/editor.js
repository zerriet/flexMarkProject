/**
 * FlexMark Editor — editor.js
 *
 * Left pane : CodeMirror 5 (full template source — HTML + Handlebars + <md> blocks)
 * Right pane: Hybrid live preview
 *   • HTML / Handlebars sections  →  rendered directly (token-substituted)
 *   • <md> blocks                 →  EasyMDE instances (editable, two-way sync)
 *
 * Two-way sync:
 *   Template edit  →  schedulePreview()  →  updatePreview()
 *                     updates HTML sections + updates EasyMDE values
 *   EasyMDE edit   →  syncMdToTemplate() →  cmEditor.setValue()
 *                     (syncing flag prevents the resulting CM5 change from
 *                      triggering a full preview rebuild)
 */

// ============================================================
// Preset loader
// ============================================================
const PRESET_TEMPLATE_URL = '/presets/business_loan_template.html';
const PRESET_DATAMAP_URL  = '/presets/business_loan_sample_data.json';

async function loadPreset() {
  const [tRes, dRes] = await Promise.all([
    fetch(PRESET_TEMPLATE_URL),
    fetch(PRESET_DATAMAP_URL)
  ]);
  if (!tRes.ok) throw new Error(`Cannot load template (${tRes.status})`);
  if (!dRes.ok) throw new Error(`Cannot load datamap (${dRes.status})`);
  const [templateText, datamapText] = await Promise.all([tRes.text(), dRes.text()]);
  return { templateText, datamapText };
}

// ============================================================
// CodeMirror 5 — flexmark overlay mode
// Highlights {{...}} tokens and <md>/<\/md> tags
// ============================================================
CodeMirror.defineMode('flexmark', function (config) {
  const base = CodeMirror.getMode(config, 'htmlmixed');
  return CodeMirror.overlayMode(base, {
    token(stream) {
      if (stream.match(/\{\{[\s\S]*?\}\}/)) return 'hbs-token';
      if (stream.match(/<\/?md>/))           return 'md-tag';
      while (stream.next() != null) {
        if (stream.peek() === '{' || stream.peek() === '<') break;
      }
      return null;
    }
  });
});

// ============================================================
// DOM refs
// ============================================================
const editorContainer = document.getElementById('editor-container');
const sampleDataEl    = document.getElementById('sampleData');
const sampleDataError = document.getElementById('sampleDataError');
const cssEditorEl     = document.getElementById('cssEditor');
const previewEl       = document.getElementById('preview');
const generateBtn     = document.getElementById('generateBtn');
const errorBanner     = document.getElementById('error-banner');

// ============================================================
// State
// ============================================================
let cmEditor  = null;    // CodeMirror 5 instance (left pane)
let mdEditors = [];      // EasyMDE instances, one per <md> block (right pane)
let syncing   = false;   // true while programmatic sync is in progress

// ============================================================
// CodeMirror 5 factory
// ============================================================
function createEditor(initialDoc) {
  const cm = CodeMirror(editorContainer, {
    value:          initialDoc,
    mode:           'flexmark',
    theme:          'monokai',
    lineNumbers:    true,
    lineWrapping:   true,
    tabSize:        2,
    indentWithTabs: false,
    extraKeys: {
      'Ctrl-Enter': generatePdf,
      'Cmd-Enter':  generatePdf
    }
  });
  cm.on('change', () => { if (!syncing) schedulePreview(); });
  return cm;
}

// ============================================================
// Segment parser
// Splits the raw template into alternating html / md segments.
// ============================================================
function parseSegments(content) {
  const segments = [];
  const re = /<md>([\s\S]*?)<\/md>/gi;
  let last = 0, m;
  while ((m = re.exec(content)) !== null) {
    if (m.index > last) segments.push({ type: 'html', text: content.slice(last, m.index) });
    segments.push({ type: 'md', text: m[1] });
    last = m.index + m[0].length;
  }
  if (last < content.length) segments.push({ type: 'html', text: content.slice(last) });
  return segments;
}

// ============================================================
// EasyMDE factory
// Creates one instance per <md> block inside the preview pane.
// ============================================================
function createMdEditor(container, initialValue, mdIndex) {
  const textarea = document.createElement('textarea');
  container.appendChild(textarea);

  const easyMde = new EasyMDE({
    element:      textarea,
    initialValue: initialValue,
    spellChecker: false,
    autofocus:    false,
    minHeight:    '120px',
    toolbar: [
      'bold', 'italic', 'strikethrough', '|',
      'heading-2', 'heading-3', '|',
      'unordered-list', 'ordered-list', '|',
      'table', 'code', 'horizontal-rule', '|',
      'preview', 'side-by-side', 'fullscreen'
    ],
    status: false,          // hide the bottom status bar
    renderingConfig: {
      singleLineBreaks: false
    }
  });

  // Sync edits back to the left-pane template
  easyMde.codemirror.on('change', () => {
    if (!syncing) syncMdToTemplate(mdIndex, easyMde.value());
  });

  return easyMde;
}

// ============================================================
// Two-way sync: EasyMDE → CM5 template
// Replaces the nth <md> block's content in the CM5 source.
// ============================================================
function syncMdToTemplate(mdIndex, newContent) {
  if (!cmEditor) return;
  const source = cmEditor.getValue();
  let count = 0;
  const updated = source.replace(/<md>([\s\S]*?)<\/md>/gi, (match, inner) => {
    return count++ === mdIndex ? `<md>${newContent}</md>` : match;
  });
  if (updated === source) return;
  syncing = true;
  cmEditor.setValue(updated);
  syncing = false;
}

// ============================================================
// Preview update
// Called after template or sample-data changes.
// Rebuilds the right pane when the segment structure changes;
// otherwise updates HTML sections and EasyMDE values in place.
// ============================================================
let previewTimer = null;

function schedulePreview() {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(updatePreview, 300);
}

function updatePreview() {
  if (!cmEditor) return;

  const segments = parseSegments(cmEditor.getValue());
  const data     = parseSampleData();

  // Count md blocks in this render pass
  const mdCount = segments.filter(s => s.type === 'md').length;

  // Check whether the DOM structure matches the current segments.
  // We store the segment count on the preview element to avoid a full DOM query.
  const needsRebuild = parseInt(previewEl.dataset.segments || '-1') !== segments.length
                    || parseInt(previewEl.dataset.mdCount  || '-1') !== mdCount;

  if (needsRebuild) {
    // Destroy old EasyMDE instances cleanly
    mdEditors.forEach(e => { try { e.toTextArea(); } catch (_) {} });
    mdEditors = [];
    previewEl.innerHTML = '';
    previewEl.dataset.segments = segments.length;
    previewEl.dataset.mdCount  = mdCount;

    let mdIdx = 0;
    segments.forEach((seg, i) => {
      const wrapper = document.createElement('div');
      wrapper.dataset.seg  = i;
      wrapper.dataset.type = seg.type;

      if (seg.type === 'html') {
        wrapper.className   = 'preview-html';
        wrapper.innerHTML   = substituteTokens(seg.text, data);
      } else {
        wrapper.className = 'preview-md';
        const idx = mdIdx++;
        previewEl.appendChild(wrapper);          // must be in DOM before EasyMDE init
        const easyMde = createMdEditor(wrapper, seg.text.trim(), idx);
        mdEditors.push(easyMde);
        return;                                  // already appended
      }

      previewEl.appendChild(wrapper);
    });

  } else {
    // In-place update: refresh html sections, sync md values if changed
    let mdIdx = 0;
    segments.forEach((seg, i) => {
      const wrapper = previewEl.querySelector(`[data-seg="${i}"]`);
      if (!wrapper) return;

      if (seg.type === 'html') {
        wrapper.innerHTML = substituteTokens(seg.text, data);
      } else {
        const easyMde       = mdEditors[mdIdx++];
        const trimmedSource = seg.text.trim();
        if (easyMde && easyMde.value() !== trimmedSource) {
          syncing = true;
          easyMde.value(trimmedSource);
          syncing = false;
        }
      }
    });
  }
}

// ============================================================
// Token substitution (flat + one-level-deep {{obj.key}})
// ============================================================
function substituteTokens(text, data) {
  return text.replace(/\{\{([^{}#/]+?)\}\}/g, (match, key) => {
    const parts = key.trim().split('.');
    let val = data;
    for (const p of parts) {
      if (val == null || typeof val !== 'object') return match;
      val = val[p];
    }
    return val !== undefined && val !== null ? String(val) : match;
  });
}

// ============================================================
// Sample data panel
// ============================================================
function parseSampleData() {
  try {
    const data = JSON.parse(sampleDataEl.value || '{}');
    sampleDataError.classList.remove('visible');
    return data;
  } catch {
    sampleDataError.classList.add('visible');
    return {};
  }
}

sampleDataEl.addEventListener('input', schedulePreview);

// ============================================================
// Bootstrap — fetch preset then initialise
// ============================================================
generateBtn.addEventListener('click', generatePdf);

(async function init() {
  previewEl.innerHTML = '<em style="color:#999">Loading preset…</em>';
  generateBtn.disabled = true;

  try {
    const { templateText, datamapText } = await loadPreset();
    cmEditor = createEditor(templateText);
    sampleDataEl.value = JSON.stringify(JSON.parse(datamapText), null, 2);
    generateBtn.disabled = false;
    schedulePreview();
  } catch (err) {
    previewEl.innerHTML =
      `<p style="color:red;padding:16px">Failed to load preset: ${err.message}</p>`;
    generateBtn.disabled = false;
  }
})();

// ============================================================
// PDF export
// ============================================================
async function generatePdf() {
  if (!cmEditor) return;
  generateBtn.disabled = true;
  generateBtn.classList.add('loading');
  hideBanner();

  try {
    const templateEncoded = unicodeSafeBase64(cmEditor.getValue());
    const cssContent      = cssEditorEl.value.trim();
    const cssEncoded      = cssContent ? unicodeSafeBase64(cssContent) : undefined;
    const documentType    = document.getElementById('documentTypeInput').value.trim();

    const payload = { templateEncoded };
    if (cssEncoded) payload.cssEncoded = cssEncoded;

    if (documentType) {
      payload.documentType = documentType;
    } else {
      const data = parseSampleData();
      if (Object.keys(data).length > 0) payload.docPropertiesJsonData = data;
    }

    const response = await fetch('/api/content/submit', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify(payload)
    });

    if (!response.ok) {
      const errText = await response.text().catch(() => `HTTP ${response.status}`);
      throw new Error(errText || `HTTP ${response.status}`);
    }

    const blob = await response.blob();
    const url  = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 10_000);

  } catch (err) {
    showBanner(err.message || 'PDF generation failed');
  } finally {
    generateBtn.disabled = false;
    generateBtn.classList.remove('loading');
  }
}

// ============================================================
// Utilities
// ============================================================
function unicodeSafeBase64(str) {
  return btoa(unescape(encodeURIComponent(str)));
}

let bannerTimer = null;
function showBanner(msg) {
  errorBanner.textContent = msg;
  errorBanner.classList.add('visible');
  clearTimeout(bannerTimer);
  bannerTimer = setTimeout(hideBanner, 6000);
}
function hideBanner() {
  errorBanner.classList.remove('visible');
}
