Java Hybrid Document Generator
1. Project Overview
This application is a specialized microservice designed to generate high-fidelity PDFs using a "Template-First" hybrid approach. It solves the common problem of needing complex layout control (HTML/CSS) combined with dynamic, user-friendly content formatting (Markdown).

Core Philosophy
Layout is HTML: Headers, footers, columns, and page breaks are handled by standard HTML templates (via Handlebars).

Content is Markdown: Dynamic text, lists, and tables are written in Markdown, allowing for clean data loops without messy HTML string concatenation.

2. Architecture Pipeline
The service follows a strict 5-stage pipeline to ensure security and formatting compliance:

Sanitization: User input is recursively scrubbed using Jsoup.clean() to prevent XSS attacks before processing.

Templating (Handlebars): Data is merged into the HTML structure. This step expands loops (e.g., iterating over a list of items) but leaves the content inside them as raw Markdown.

DOM Processing (Jsoup & Flexmark):

The hybrid string is parsed into a Jsoup Document.

The service locates custom <md> tags.

Content inside tags is rendered to HTML using Flexmark.

The <md> nodes are replaced with the rendered HTML nodes in-place.

Assembly: CSS, Headers, and Footers are injected directly into the DOM to ensure valid XHTML syntax (required for the renderer).

Rendering (Flying Saucer): The strict XHTML is converted to a PDF binary.

3. Recent Updates (v2.0 Optimization)
Performance Refactor: Regex vs. DOM
Previous Implementation:

Used Regex to find <md> blocks in a massive string.

Rendered Markdown separately and used string concatenation to rebuild the HTML.

Drawback: Fragile string manipulation; performance degradation on large files.

Current Implementation:

Single-Pass Parsing: The document is parsed into a Jsoup Object Model immediately after templating.

In-Place Modification: We traverse the DOM tree to find <md> elements and replace them directly.

Benefit: Faster execution, lower memory overhead (no duplicate string buffers), and significantly higher robustness against malformed tags.

CSS Compliance
Engine: Currently using xhtmlrenderer (Flying Saucer).

Limitations: Strictly supports CSS 2.1 (No Flexbox/Grid).

Mitigation: The service is configured to silently ignore unsupported CSS 3 properties (like direction: ltr) to prevent crashing, ensuring stability even with modern CSS frameworks.

4. Technical Constraints
Strict XHTML: All HTML tags must be closed (e.g., <br />, not <br>). The Jsoup assembly step enforces this automatically.

CSS Support: Layouts must use float, table-cell, or position: absolute. Modern Flexbox layouts are not supported by the current rendering engine.