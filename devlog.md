# Frontend Development Log

Tracks changes, decisions, and issues encountered during development of `src/main/resources/static/index.html`.

---

## 2026-03-27 — Air-Gap / CDN Removal

### Change
Removed all runtime CDN dependencies so the editor works in air-gapped and secure environments. Three libraries were inlined directly into `index.html`:

| Library | Version | Original format | Inline approach |
|---|---|---|---|
| CodeJar | 4.3.0 | ESM (`export function CodeJar`) | Copied ESM source into `<script type="module">`; removed `export` keyword |
| Prism.js | 1.30.0 | IIFE (sets `window.Prism`) | Copied as a separate `<script>` block before the module script |
| Marked | 17.0.5 | UMD (sets `window.marked` in browser) | Copied as a separate `<script>` block before the module script |

**File size after inlining:** ~145 KB

### Rationale
- No network calls on page load — works fully offline
- Eliminates supply-chain risk from CDN-hosted scripts
- Versions are frozen at known-good releases

### Notes
- CodeJar has no IIFE/UMD build; only ESM is published. The `export` keyword was removed to make it compatible with the module script's internal scope.
- `marked.parse()` output is assigned to `innerHTML` — this is pre-existing behaviour, not introduced here. The input is developer-authored template content, not user-supplied data.
- To upgrade a library, replace its inlined block and update this log.

---

## 2026-03-27 — Document Settings Drawer

### Change
Added a collapsible **Document Settings** drawer to the right side of the editor UI. The drawer provides a user-friendly interface for features previously only accessible via raw JSON in the API.

**Three-state behaviour:** expanded (260 px) → rail (44 px icon strip) → hidden (0 px). Cycled by the "Document settings" toolbar button or the chevron inside the drawer.

**Drawer sections:**
- **Template** — Import a template from a base64 code string (e.g. shared by a colleague); export the current editor content as a code string.
- **Page header** — Paste a base64-encoded HTML header; it will be prepended to every page of the generated PDF.
- **Page footer** — Same as header, appended to every page.
- **Payload inspector** (footer, collapsed by default) — Shows a truncated preview of the JSON body that will be sent on the next Generate PDF click. Useful for debugging and copying the full payload.

**Status dots:** Each section and the toolbar button show a colour-coded dot (neutral / green / red) reflecting whether a valid, invalid, or no value is set.

### Implementation notes
Four injection points were applied to `index.html`:

| Point | What was added |
|---|---|
| A — CSS | `:root` drawer variables, `#app-body` / `#main-col` flex layout, `#doc-drawer` three-state transitions, all drawer component styles |
| B — Toolbar HTML | `<button id="docSettingsBtn">` with three status dots |
| C — Layout HTML | Wrapped existing split-pane + sidebar in `#main-col`; added `#doc-drawer` with header, rail col, body sections, and footer |
| D — JavaScript | `generatePdf` enhanced with header/footer reads; drawer state machine; base64 helpers; template import/export; field dot updates; payload inspector |

**Design constraints followed:**
- Catppuccin Mocha dark palette throughout
- User-facing labels use plain language — "code string" not "base64", "header" not "headerEncoded"
- All drawer `onclick` handlers exposed on `window.*` so they are reachable from HTML attributes inside the ES module scope

---

## 2026-03-27 — Bug Fixes (Post-Drawer)

### Bug 1 — PDF generation button had no effect after drawer was added

**Symptom:** Clicking "Generate PDF" did nothing. No network request was sent and no error message appeared.

**Root cause:** Temporal dead zone (TDZ) crash on module load.

`setDrawerState('hidden')` is called at initialisation time. Inside, it calls `updatePayloadIfOpen()`, which reads `payloadOpen`. However, `let payloadOpen = false` was declared further down in the file, after the call site.

`let` declarations are hoisted to the top of their scope but are **not initialised** until the declaration is evaluated. Accessing the variable before that point throws:

```
Uncaught ReferenceError: Cannot access 'payloadOpen' before initialization
    at updatePayloadIfOpen
    at setDrawerState
```

Because the error occurred during module initialisation, execution stopped before `generateBtn.addEventListener('click', generatePdf)` could run. The button was permanently inert.

**Fix:** Moved `let payloadOpen = false` to immediately before the drawer state machine block (alongside `let drawerState`), ensuring it is initialised before `setDrawerState` is first called. Removed the now-duplicate declaration from its original location.

---

### Bug 2 — "View full request (advanced)" appeared non-functional

**Symptom:** The payload inspector toggle in the drawer footer could not be expanded.

**Root cause:** `.payload-toggle { color: #3a3a58 }` — a near-black colour effectively invisible against the `#0f0f1b` footer background. The element was present and functional; it simply could not be seen to click.

**Fix:** Changed `.payload-toggle { color }` from `#3a3a58` to `var(--text-muted)` (`#6c7086`).

---

### Bug 3 — Drawer text was difficult to read

**Symptom:** Section descriptions, import zone labels, placeholder text, and payload preview JSON were all very hard to read.

**Root cause:** Several CSS rules used near-black colour values too dark for the Catppuccin Mocha dark backgrounds:

| Selector | Old colour | Background |
|---|---|---|
| `.ds-desc` | `#585870` | `#13131f` |
| `.ds-import-label` | `#3a3a58` | `#0d0d1c` |
| `.payload-toggle` | `#3a3a58` | `#0f0f1b` |
| `.payload-pre` | `#3a3a58` | `#090912` |
| `.ds-textarea::placeholder` | `#2a2a44` | `#090912` |

**Fix:** Updated all affected rules to `var(--text-muted)` (`#6c7086`) — the same muted text token used for `.ds-hint` and `.ds-section-label` elsewhere in the drawer. Placeholder colour raised to `#45455a` (intentionally slightly dimmer than body text, as is conventional for placeholders).

---

## Pending / Known Limitations

- The payload inspector preview does not include `queryParams` when a DB document type is selected with custom parameters.
- Drawer state (expanded / rail / hidden) is not persisted across page reloads.
- Library versions are frozen at inlining time. Upgrades require manually replacing the inlined block and retesting.
