# Revision History

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
