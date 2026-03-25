# Java Hybrid Document Generator — Feature Expansion Plan
**Markdown Editor · Database Data Injection**

| | |
|---|---|
| **Document Type** | Feature Expansion Plan — Internal |
| **Status** | Draft — For Manager Review |
| **Date** | March 2026 |
| **Author** | Mark, AI Engineer |

---

## Table of Contents
1. [Context & Background](#1-context--background)
2. [Feature 1 — Split Markdown Editor with Live Preview](#2-feature-1--split-markdown-editor-with-live-preview)
3. [Feature 2 — Database-Driven Data Injection](#3-feature-2--database-driven-data-injection)
4. [Combined Development Roadmap](#4-combined-development-roadmap)
5. [Claude Code Implementation Notes](#5-claude-code-implementation-notes)
6. [Decisions Required from Manager](#6-decisions-required-from-manager)

---

## 1. Context & Background

The **Java Hybrid Document Generator** is a Spring Boot microservice that generates high-fidelity PDFs via a template-first hybrid approach. Layout is controlled through Handlebars-driven HTML/CSS templates, while dynamic content is authored in Markdown using custom `<md>` tags processed via Flexmark. The engine renders to PDF using iText7 html2pdf.

A manager request has been raised to expand the project in two directions:

- A split Markdown editor with live preview to improve template maintainability for business users
- Database-driven data injection to replace the current JSON map payload, enabling dynamic, query-driven document generation

This document defines the scope, architecture, development phases, and open questions for both features.

---

## 2. Feature 1 — Split Markdown Editor with Live Preview

### 2.1 Objective

Extend the existing lightweight HTML frontend with a split-pane editor that allows business users to author and iterate on document templates without touching the underlying codebase. The editor should provide:

- A left pane for template authoring (Handlebars + HTML + `<md>` blocks)
- A right pane showing a live Markdown-rendered preview
- A PDF export trigger to invoke the backend and download the generated PDF
- Syntax highlighting for Handlebars variables and `<md>` tags

### 2.2 Scope

| In Scope | Out of Scope |
|---|---|
| Split-pane editor (template + preview) | Full WYSIWYG editor hiding template syntax |
| Live Markdown render of `<md>` blocks | Template version control / history |
| Handlebars variable highlighting | Multi-user collaborative editing |
| Sample data injection for preview | Template persistence / database storage |
| PDF export button (calls backend API) | CSS editor pane (Phase 2 candidate) |

### 2.3 Technical Approach

The editor will be built as an extension of the existing HTML frontend. No framework migration is required — the implementation uses vanilla JS with lightweight library augmentation.

#### Editor Library

**Recommended: CodeMirror 6** (MIT licensed). Chosen for:

- Modular architecture — only load what is needed
- First-class support for custom language modes (required for Handlebars + HTML hybrid syntax)
- Active maintenance and good mobile/touch support
- No build toolchain required — can be loaded via CDN for minimal frontend complexity

#### Preview Rendering

The right pane renders the Markdown portions of the template using **Marked.js** (CDN). The preview pipeline is:

1. On editor keypress (debounced 300ms), extract all content within `<md>…</md>` tags
2. Substitute any `{{variable}}` tokens with values from the sample data form
3. Render extracted Markdown blocks via Marked.js
4. Re-inject rendered HTML into corresponding preview pane segments
5. Non-`<md>` HTML segments are rendered as-is in the preview

> **NOTE:** Full PDF fidelity cannot be replicated in the browser preview — CSS features like Flexbox, page breaks, and custom fonts will differ from the iText7 output. The preview is intended as a content accuracy check, not a pixel-perfect render. A dedicated **Generate PDF** button triggers the actual backend and opens the binary in a new tab.

#### PDF Export

The export button POSTs to `POST /api/content/submit` with:

- `templateEncoded`: Base64 of the editor content
- `cssEncoded`: Base64 of the CSS pane (or a stored default)
- `docPropertiesJsonData`: Populated from the sample data JSON input
- `headerEncoded` / `footerEncoded`: Optional, from secondary editor panes

Response (PDF binary) is streamed to the browser as a Blob URL and opened inline.

### 2.4 Development Phases

| Phase | Name | Deliverables | Effort Est. |
|---|---|---|---|
| 1A | Editor Shell | Replace textarea with CodeMirror 6 instance; basic HTML syntax highlighting; resizable split pane layout | 1–2 days |
| 1B | Live Preview | Debounced Markdown preview via Marked.js; `<md>` tag extraction; Handlebars token substitution with sample data | 2–3 days |
| 1C | PDF Export | Export button → `POST /api/content/submit`; Blob URL response handling; loading/error states | 1 day |
| 1D | UX Polish | Handlebars `{{}}` syntax highlighting; `<md>` tag highlighting; responsive layout; keyboard shortcuts | 1–2 days |

### 2.5 Open Questions

- Should templates be saveable to the server/database, or is the editor session-only?
- Does the business user need a CSS editor pane, or will CSS be managed by developers only?
- Is sample data entered manually in the UI, or pulled from a mock DB connection (links to Feature 2)?
- Are header/footer templates editable in the same UI, or a separate concern?

---

## 3. Feature 2 — Database-Driven Data Injection

### 3.1 Objective

Replace the ad-hoc `docPropertiesJsonData` JSON map payload with a structured database query layer. The service should be able to resolve a named data context (e.g. a report type or document profile) to a set of query definitions that populate the Handlebars template variables at generation time.

### 3.2 Feasibility Assessment Scope

Given that the DB schema is not yet defined, this phase is a **feasibility POC using a mock/hardcoded database**. The goal is to prove the injection pattern and surface design constraints before committing to a schema.

The POC will demonstrate:

- A named template-to-query mapping (e.g. `"monthly_report"` → set of SQL definitions)
- Query execution against a mock in-memory H2 database seeded with representative data
- Result mapping to the flat key-value structure expected by the Handlebars context
- Multi-table joins handled transparently — the template author only sees resolved variable names
- The existing `POST /api/content/submit` endpoint extended with an optional `documentType` parameter

### 3.3 Proposed Architecture

#### New Request Model

The API will support two modes, maintaining backward compatibility:

| Mode | Request Field | Behaviour |
|---|---|---|
| Legacy (existing) | `docPropertiesJsonData` populated | JSON map used directly as Handlebars context. No DB query. |
| DB Mode (new) | `documentType` string provided | `documentType` resolved to query definitions; DB queried; result mapped to Handlebars context. |

#### Component Design

Three new components are introduced:

| Component | Layer | Responsibility |
|---|---|---|
| `DocumentTypeRegistry` | Service | Holds the mapping from `documentType` names to one or more `QueryDefinition` objects. POC: hardcoded. Production: DB or config-driven. |
| `QueryDefinition` | Model | Encapsulates a named SQL string, its parameter bindings, and a result mapping strategy (`SINGLE_ROW`, `LIST`, or `SCALAR`). |
| `DataContextResolver` | Service | Executes `QueryDefinition`s via `JdbcTemplate`, flattens results into a `Map<String, Object>` compatible with the existing Handlebars rendering pipeline. |

#### POC Data Flow

1. Request arrives with `documentType: "monthly_report"`
2. `DocumentTypeRegistry` resolves to a list of `QueryDefinition`s
3. `DataContextResolver` executes each query against H2 mock DB
4. Results are merged into a single `Map<String, Object>`
5. Map is passed to the existing `MarkdownService` as the Handlebars context
6. PDF generated and returned — unchanged from current pipeline

> **KEY DESIGN CONSTRAINT:** The existing Handlebars rendering pipeline (`MarkdownService`) requires **zero modification**. The DB layer is a pre-processing step that resolves to the same `Map<String, Object>` the service already consumes. This keeps the POC low-risk and fully backward compatible.

#### Mock Database (H2)

The POC uses Spring Boot's embedded H2 database with `schema.sql` and `data.sql` seed files. No external DB setup required — isolated to the `poc` Spring profile. Representative tables:

- `customers` — id, name, account_number, tier
- `transactions` — id, customer_id, date, amount, description
- `report_metadata` — report_id, generated_by, period_start, period_end

### 3.4 Development Phases

| Phase | Name | Deliverables | Effort Est. |
|---|---|---|---|
| 2A | H2 POC Setup | H2 dependency; `schema.sql` + `data.sql` seed; Spring profile configuration (`poc`, `default`) | 0.5 days |
| 2B | Registry + Resolver | `DocumentTypeRegistry` (hardcoded); `QueryDefinition` model; `DataContextResolver` with `JdbcTemplate`; unit tests | 2 days |
| 2C | API Integration | Extend `GenerateRequestDto` with optional `documentType` field; controller routing logic; integration test with H2 | 1 day |
| 2D | Feasibility Report | Document POC findings: query latency, mapping limitations, multi-table join handling, production readiness gaps | 0.5 days |

### 3.5 Open Questions / Risk Flags

- **Schema ownership:** who defines and maintains the `QueryDefinition`s in production — developer or business?
- **List rendering:** Handlebars `{{#each}}` loops require array values. Multi-row query results must be mapped to `List<Map<String, Object>>` — confirm Flexmark/Handlebars handles this cleanly.
- **Security:** parameterised queries must be enforced; no dynamic SQL construction from user input.
- **Transaction scope:** PDF generation is stateless; DB reads should be read-only and connection-pooled appropriately.
- **Production DB:** H2 is POC-only. The production driver (PostgreSQL, MSSQL, Oracle) must be confirmed before schema design begins.

---

## 4. Combined Development Roadmap

| Sprint | Phase | Feature | Key Deliverable | Effort |
|---|---|---|---|---|
| 1 | 1A + 1B | Editor | CodeMirror shell + live Markdown preview | 3–4 days |
| 1 | 2A | DB Layer | H2 setup + seed data | 0.5 days |
| 2 | 1C + 1D | Editor | PDF export + UX polish | 2–3 days |
| 2 | 2B + 2C | DB Layer | Registry, Resolver, API integration | 3 days |
| 3 | 2D | DB Layer | Feasibility report + risk assessment | 0.5 days |
| 3 | — | Integration | Wire editor sample data input → DB resolver for end-to-end demo | 1 day |

**Total estimated effort: 10–12 developer days** (excludes manager review cycles and schema finalisation for Feature 2).

---

## 5. Claude Code Implementation Notes

Feed this document as context at the start of each Claude Code session. Reference the section and phase number relevant to the current task.

### 5.1 Repository Context

- **Project:** Spring Boot microservice, package `com.flexmark.flexMarkProject`
- **PDF engine:** iText7 html2pdf
- **Template engine:** Handlebars.java
- **Markdown engine:** Flexmark
- **HTML parsing:** Jsoup
- **Existing frontend:** lightweight HTML — no framework, no build toolchain
- **API endpoint:** `POST /api/content/submit` → returns PDF binary

### 5.2 Feature 1 — Editor Prompting Guide

#### Phase 1A — Editor Shell
```
Extend the existing HTML frontend with a split-pane layout using CodeMirror 6 loaded
from CDN. Left pane is the template editor (HTML + Handlebars syntax), right pane is
the preview. Use resizable panels. Do not introduce a JS framework or build toolchain.
```

#### Phase 1B — Live Preview
```
Add live Markdown preview to the right pane. On editor change (debounced 300ms),
extract all content between <md> and </md> tags, substitute {{variable}} tokens with
values from a sample data JSON textarea, render via Marked.js CDN, and display in the
preview pane.
```

#### Phase 1C — PDF Export
```
Add a 'Generate PDF' button. On click, Base64-encode the editor content as
templateEncoded, the CSS textarea as cssEncoded, and parse the sample data textarea as
docPropertiesJsonData. POST to /api/content/submit, receive the binary response, create
a Blob URL, and open the PDF in a new browser tab.
```

#### Phase 1D — UX Polish
```
Add syntax highlighting for Handlebars {{variable}} tokens and <md> tags within the
CodeMirror editor. Improve split-pane responsiveness and add keyboard shortcuts for
common actions (e.g. Ctrl+Enter to trigger PDF export).
```

### 5.3 Feature 2 — DB Layer Prompting Guide

#### Phase 2A — H2 POC Setup
```
Add H2 in-memory database dependency to pom.xml (scope: runtime, profile: poc).
Create src/main/resources/schema.sql with tables: customers, transactions,
report_metadata. Create data.sql with 5–10 rows of representative seed data per table.
Isolate H2 config to application-poc.properties.
```

#### Phase 2B — Registry + Resolver
```
Create a QueryDefinition model class (fields: name, sql, resultType enum:
SINGLE_ROW / LIST / SCALAR). Create DocumentTypeRegistry as a Spring @Service with a
hardcoded Map of documentType names to List<QueryDefinition>. Create DataContextResolver
that uses JdbcTemplate to execute each QueryDefinition and merges results into
Map<String, Object>. Write unit tests for both.
```

#### Phase 2C — API Integration
```
Extend GenerateRequestDto with an optional String documentType field. In the controller,
if documentType is present and docPropertiesJsonData is null, invoke DataContextResolver
to build the data context before passing to MarkdownService. Maintain full backward
compatibility — existing JSON map behaviour must be unchanged.
```

### 5.4 General Claude Code Guidance

- Always maintain backward compatibility — the existing `POST /api/content/submit` contract must not break
- Add Spring profiles (`application-poc.properties`) to isolate H2 configuration from production
- Write unit tests for `DataContextResolver` and `DocumentTypeRegistry` — these are the core new logic units
- Avoid introducing new dependencies without justification — the project has a clean, minimal dependency footprint
- Keep frontend changes in the existing HTML file structure — no webpack, no npm, no framework

---

## 6. Decisions Required from Manager

| # | Question | Options | Impacts |
|---|---|---|---|
| 1 | Should templates be persistable from the editor UI? | A) Session-only · B) Save to filesystem · C) Save to database | Frontend scope; Feature 2 schema |
| 2 | Who manages `QueryDefinition`s in production? | A) Developer-only (config file) · B) Business user (admin UI) · C) Hybrid | Feature 2 production design |
| 3 | What is the target production database? | PostgreSQL / MSSQL / Oracle / other | Feature 2 driver + schema |
| 4 | Is a CSS editor pane required for business users? | A) No — CSS is developer-managed · B) Yes — include in editor | Feature 1 scope |

---

*End of document. Feed this file as context into Claude Code and reference the section and phase number relevant to the current task.*
