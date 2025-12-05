# Java Hybrid Document Generator

## Project Overview
This application is a specialized microservice that generates high-fidelity PDFs using a **template-first hybrid** approach. It combines strict HTML/CSS layout control with **Markdown** for dynamic, user-friendly content formatting.

### Core Philosophy
- **Layout is HTML:** Headers, footers, columns, and page breaks are handled by Handlebars-driven HTML templates.
- **Content is Markdown:** Dynamic text, lists, and tables stay as Markdown for clean data loops without brittle string concatenation.

## Architecture Pipeline
The service follows a strict **5-stage pipeline** to ensure security and formatting compliance:

1) **Sanitization:** User input is recursively scrubbed with `Jsoup.clean()` to prevent XSS before processing.
2) **Templating (Handlebars):** Data merges into the HTML structure; loops expand while embedded content remains raw Markdown.
3) **DOM Processing (Jsoup & Flexmark):**
   - Parse the hybrid string into a Jsoup `Document`.
   - Locate custom `<md>` tags.
   - Render the enclosed Markdown to HTML via Flexmark.
   - Replace each `<md>` node in place with the rendered HTML nodes.
4) **Assembly:** CSS, headers, and footers are injected directly into the DOM to ensure valid XHTML syntax for the renderer.
5) **Rendering (Flying Saucer):** The strict XHTML is converted to a PDF binary.

## Recent Updates (v2.0 Optimization)

### Performance Refactor: Regex vs. DOM
- **Previous:** Regex to find `<md>` blocks, then separate render + string concatenation to rebuild HTML (fragile and slow on large inputs).
- **Current:** Single-pass parse into a Jsoup object model; traverse to replace `<md>` elements in place.
- **Benefit:** Faster execution, lower memory overhead (no duplicate string buffers), and higher robustness against malformed tags.

### CSS Compliance
- **Engine:** `xhtmlrenderer` (Flying Saucer).
- **Limitations:** Supports CSS 2.1 only (no Flexbox/Grid).
- **Mitigation:** Silently ignores unsupported CSS 3 properties (e.g., `direction: ltr`) to avoid crashes while remaining compatible with modern CSS.

## Technical Constraints
- **Strict XHTML:** All HTML tags must be closed (e.g., `<br />`, not `<br>`). The Jsoup assembly step enforces this automatically.
- **CSS support:** Layouts must use `float`, `table-cell`, or `position: absolute`; Flexbox layouts are **not** supported by the current rendering engine.