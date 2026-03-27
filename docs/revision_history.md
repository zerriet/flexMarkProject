# Revision History

---

## [Unreleased] — 2026-03-27

### Enhancement: Air-Gap / Offline Frontend (CDN Libraries Inlined)

Removes all CDN dependencies from `src/main/resources/static/index.html` so the editor works in fully isolated (air-gapped) environments with no outbound internet access.

**Previously** the page fetched three libraries from `https://esm.sh` on every load:

| Library | Version | Role |
|---|---|---|
| `codejar` | 4.3.0 | Template editor (left pane) |
| `prismjs` | 1.30.0 | Syntax highlighting (`{{...}}`, `<md>` tokens) |
| `marked` | 17.0.5 | Markdown → HTML in the preview pane |

**After this change** all three are inlined directly in `index.html`:
- `prismjs` and `marked` inlined as `<script>` blocks (IIFE/UMD builds) before the module script — they expose `window.Prism` and `window.marked` as globals
- `codejar` source inlined at the top of the `<script type="module">` block (ESM export keyword removed; function is used directly)
- The three `import … from 'https://esm.sh/…'` lines removed

`index.html` remains the only file required to run the frontend. File size increased from ~32 KB to ~145 KB.

#### Security notes
- **Supply-chain risk reduced**: libraries are no longer fetched from an external CDN at runtime; a compromised CDN can no longer affect the running app
- **Version freeze**: library versions are now fixed — security patches require manually re-inlining updated versions
- **Pre-existing**: `marked.parse()` output is set as `innerHTML` in the preview pane without sanitisation; acceptable for this localhost developer tool but should be revisited if the app is ever exposed to multiple users

---

### Enhancement: PostgreSQL Local Profile

Adds a dedicated Spring `local` profile backed by a real PostgreSQL instance, allowing the application to run against a local `docforge` database instead of the in-process H2 store.

#### Modified / New
| File | Change |
|---|---|
| `pom.xml` | Added `org.postgresql:postgresql` driver at `runtime` scope |
| `src/main/resources/application-local.properties` | New Spring profile `local`: `jdbc:postgresql://localhost:5432/docforge`, `spring.sql.init.mode=always`, H2 console disabled, Hikari pool capped at 5 connections |

Run with: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

---

### Enhancement: SQL Idempotency & Schema Reset

Makes startup SQL safe to re-run without manual intervention.

| File | Change |
|---|---|
| `src/main/resources/schema.sql` | Added `DROP TABLE IF EXISTS … CASCADE` for all 5 tables before `CREATE TABLE IF NOT EXISTS`, enabling a clean schema reset on each startup |
| `src/main/resources/data.sql` | Added `ON CONFLICT DO NOTHING` to all 5 `INSERT` statements — seed data is now idempotent |

---

### Enhancement: Data Source Selector in Editor UI

Replaces the manual Document Type text field with a first-class toolbar dropdown, making DB-driven mode discoverable without needing to know a registered type string.

Changes to `src/main/resources/static/index.html`:
- Added a **Data Source** `<select>` dropdown to the toolbar, styled identically to the preset selector
- Options: `"Sample Data"` (default / empty value) and `"DB: business_loan_report"`
- When DB mode is active, the Sample Data side panel is visually dimmed (`opacity: 0.35`, pointer-events disabled) via `.side-panel.db-mode` CSS class toggled by the `change` event
- `generatePdf()` sends `documentType` from the dropdown when a DB source is selected; falls back to `docPropertiesJsonData` from the sample data panel otherwise

---

### Bug Fix: Document Settings Drawer — CSS Colour Corrections

Four CSS colour values in `src/main/resources/static/index.html` were documented as fixed in `devlog.md` but were not applied to the file during the IntelliJ Claude plugin session. Applied manually.

| Selector | Property | Old value | New value |
|---|---|---|---|
| `.ds-import-label` | `color` | `#3a3a58` | `var(--text-muted)` |
| `.ds-textarea::placeholder` | `color` | `#2a2a44` | `#45455a` |
| `.payload-toggle` | `color` | `#3a3a58` | `var(--text-muted)` |
| `.payload-pre` | `color` | `#3a3a58` | `var(--text-muted)` |

All four values were near-black, effectively invisible against the dark Catppuccin Mocha drawer backgrounds. The import zone label, textarea placeholders, payload inspector toggle, and payload preview text are now legible.

---

### Bug Fix: DB Data Source — SQL Column Alias Mismatch

`DocumentTypeRegistry.java` used descriptive column aliases that did not match the variable names in the Preset 2 (Loan Offer Letter) template. When the **DB: business_loan_report** data source was selected, 10 template tokens resolved to blank — producing a PDF with empty borrower details, loan figures, and bank officer name.

**Root cause:** The SQL `AS` aliases in `buildBusinessLoanQueries()` used verbose names (e.g. `"registrationNumber"`, `"principalFormatted"`) while the template referenced the shorter names used in the frontend sample JSON (e.g. `{{borrower.reg}}`, `{{loan.principal}}`).

**Fix:** Updated SQL aliases in `DocumentTypeRegistry.java` to exactly match the template variable names. No schema, seed data, or template changes required.

| Query | Column | Old alias | New alias |
|---|---|---|---|
| `borrower` | `registration_number` | `registrationNumber` | `reg` |
| `borrower` | `registered_address` | `registeredAddress` | `address` |
| `borrower` | `business_type` | `businessType` | `type` |
| `borrower` | `authorised_signatory` | `authorisedSignatory` | `signatory` |
| `borrower` | `signatory_designation` | `signatoryDesignation` | `designation` |
| `loan` | `principal_formatted` | `principalFormatted` | `principal` |
| `loan` | `tenure_months` | `tenureMonths` | `tenure` |
| `loan` | `monthly_instalment_fmt` | `monthlyInstalmentFormatted` | `instalment` |
| `loan` | `first_repayment_date` | `firstRepaymentDate` | `firstRepayment` |
| `bank` | `officer_name` | `officerName` | `officer` |

---

## [Unreleased] — 2026-03-25

### Feature 2: Database-Driven Data Injection (POC)

Introduces an optional database-backed data resolution path. Callers can now send a `documentType` identifier instead of a full `docPropertiesJsonData` JSON map; the server resolves the Handlebars context from H2 using registered SQL queries. The existing inline-JSON path is fully preserved and takes precedence when both fields are supplied.

#### New — Backend Infrastructure
| File | Description |
|---|---|
| `pom.xml` | Added `spring-boot-starter-jdbc` dependency |
| `src/main/resources/application-poc.properties` | New Spring profile `poc`: H2 in PostgreSQL compatibility mode (`MODE=PostgreSQL`), H2 web console enabled at `/h2-console` |
| `src/main/resources/schema.sql` | DDL for 5 tables: `loan_agreements`, `borrowers`, `repayment_schedule`, `loan_security`, `bank_officers` |
| `src/main/resources/data.sql` | Seed data for the `business_loan_report` document type, derived from `docs/datamap/business_loan_sample_data.json` (12-row repayment schedule, borrower, loan terms, bank officer) |

#### New — DB Domain Package (`com.flexmark.flexMarkProject.db`)
| Class | Description |
|---|---|
| `QueryDefinition` | Value class holding a named SQL query: `contextKey` (Handlebars map key), `sql`, `ResultType` (SINGLE_ROW / LIST / SCALAR), `staticParams` |
| `DocumentTypeRegistry` | `@Service` holding a hardcoded `Map<String, List<QueryDefinition>>`. Registered types: `"business_loan_report"` (6 queries: agreementRef, issueDate, borrower, loan, schedule, bank) |
| `DataContextResolver` | `@Service` that executes queries via `NamedParameterJdbcTemplate`, merges runtime params over static params, and returns `Map<String, Object>` for Handlebars consumption |

#### Modified — API Layer
| File | Change |
|---|---|
| `GenerateRequestDto` | Added optional fields `documentType: String` and `queryParams: Map<String, Object>`. `docPropertiesJsonData` takes precedence when both are present. |
| `InitialController` | Injects `DataContextResolver`; adds pre-processing branch before `MarkdownService.generateDocument()`: if `documentType` is set and `docPropertiesJsonData` is absent, resolves data from DB. `MarkdownService` is unchanged. |

#### New — Tests
| Test Class | Type | Count |
|---|---|---|
| `DocumentTypeRegistryTest` | Unit (no Spring context) | 5 |
| `DataContextResolverTest` | Spring integration (`@SpringBootTest`) | 7 |
| `InitialControllerDbIntegrationTest` | Spring integration (`@SpringBootTest`) | 3 |

**Total test count: 66** (up from 51)

---

### Feature 1: Split-Pane Editor with Live Preview

A browser-based authoring tool served directly by Spring Boot at `http://localhost:8080/`. No separate Node server or build toolchain required. All dependencies loaded from CDN via plain `<script>` tags — no bundler, no import maps.

#### New — Static Frontend (`src/main/resources/static/`)
| File | Description |
|---|---|
| `index.html` | Shell: CSS Grid two-column layout, toolbar with Document Type input and Generate PDF button, CodeMirror 5 loaded from cdnjs, EasyMDE and Marked.js from jsDelivr, collapsible Sample Data and CSS panels |
| `editor.css` | Dark theme (Catppuccin-inspired), split-pane grid, collapsible panel styles, preview pane typography, EasyMDE card styling within the preview pane |
| `editor.js` | Full editor logic: CodeMirror 5 with custom `flexmark` overlay mode, `{{...}}` and `<md>` token highlighting, segment-based live preview pipeline, EasyMDE two-way sync, Unicode-safe Base64 PDF export, `Cmd/Ctrl+Enter` keyboard shortcut |
| `presets/business_loan_template.html` | Copy of `docs/templates/business_loan_template.html`, served at `/presets/business_loan_template.html` and pre-loaded into the editor on page load |
| `presets/business_loan_sample_data.json` | Copy of `docs/datamap/business_loan_sample_data.json`, served at `/presets/business_loan_sample_data.json` and pre-loaded into the Sample Data panel on page load |

#### Editor Architecture

**Left pane — CodeMirror 5**
- Custom `flexmark` overlay mode layered over `htmlmixed`: highlights `{{...}}` tokens (yellow) and `<md>`/`</md>` tags (green)
- Monokai theme, line numbers, soft-wrap, 2-space indent
- `Cmd/Ctrl+Enter` shortcut triggers PDF generation

**Right pane — Hybrid live preview**
- Template source is parsed into alternating `html` and `md` segments via regex on `<md>...</md>` tags
- `html` segments: rendered with Handlebars-style token substitution (flat and one-level-deep `{{key}}` / `{{obj.key}}`) then set as `innerHTML`
- `md` segments: each rendered as a live **EasyMDE** instance (CodeMirror 5-based Markdown editor) in an editable card, replacing the static Marked.js render
- Preview rebuilds from scratch only when the segment count or `<md>` block count changes; otherwise updates in-place to preserve EasyMDE focus and cursor position

**Two-way sync**
- EasyMDE edit → `syncMdToTemplate()`: replaces the corresponding `<md>...</md>` block in the CM5 source using a `syncing` flag to suppress the resulting CM5 `change` event
- CM5 template edit → `schedulePreview()` (300 ms debounce) → `updatePreview()`: refreshes HTML sections and updates EasyMDE values where the source has changed

#### Editor Behaviour Notes
- **`<md>` blocks are editable in the preview:** Each `<md>` block is rendered as an EasyMDE editor with a Markdown toolbar. Changes sync back to the left-pane template in real time, mirroring a Jira/Confluence inline editing experience.
- **Preview fidelity:** The right pane shows content intent, not pixel-exact PDF output. EasyMDE renders Markdown client-side; the server uses Flexmark with extensions (`TablesExtension`, `AttributesExtension`) — complex tables and code fences may differ in the final PDF.
- **Token substitution in preview:** Flat and one-level-deep `{{key}}` and `{{obj.key}}` tokens are substituted from the Sample Data panel. `{{#each}}` loops and `{{#if}}` blocks are not expanded in the preview; they render correctly in the generated PDF.
- **Preset loading:** On startup the editor fetches the business loan template and sample data JSON from `/presets/`. The editor is immediately usable without any manual input.
- **DB-driven mode:** Enter a registered document type (e.g. `business_loan_report`) in the Document Type toolbar field. The editor sends `documentType` to the API instead of the sample data JSON, and the server resolves data from H2.
- **Unicode safety:** The editor uses `btoa(unescape(encodeURIComponent(...)))` for Base64 encoding to handle non-ASCII content (e.g. company names, currency symbols) correctly.

---

## v6.2 — Prior releases

> The following entries are reproduced from the existing `README.md` Recent Updates section for continuity.

### v6.2 — Direct Image Parsing (PR #5)
- SSRF-protected `SecureDataUriResourceRetriever` inner class inside `MarkdownService`
- Data URIs parsed per RFC 2397; inline Base64 images decoded on-the-fly without temp files
- External HTTP/HTTPS requests blocked; `file://` and `jar:` resources allowed
- 15 security tests added

### v6.1 — iText7 Migration (PR #4)
- Replaced previous PDF engine with iText7 8.0.5 + html2pdf 5.0.5
- Improved CSS rendering fidelity (Flexbox, Grid)

### v6.0 — Swagger / OpenAPI (PR #3 area)
- Added Springdoc OpenAPI 2.6.0
- Full Swagger UI at `/swagger-ui.html` with example payloads and error schemas

### v2.0 — Handlebars + Flexmark Integration
- Template-first hybrid architecture established
- `<md>` tag convention introduced for Markdown blocks within HTML templates
- Flexmark extensions: `TablesExtension`, `AttributesExtension`
