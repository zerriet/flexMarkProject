package com.flexmark.flexMarkProject.service;

import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.safety.Safelist;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
/**
 * Service layer orchestrating the "Template-First" Document Generation Pipeline.
 * <p>
 * <strong>Architecture Overview:</strong>
 * Unlike traditional pipelines that convert Markdown data before insertion, this service
 * processes the Handlebars template <em>first</em>. This allows the template to contain
 * Markdown logic (like loops creating table rows) which are then rendered into HTML
 * in a second pass.
 * </p>
 * <p>
 * <strong>Pipeline Stages:</strong>
 * <ol>
 * <li><strong>Sanitization:</strong> recursively cleans user input to prevent XSS.</li>
 * <li><strong>Merge (Handlebars):</strong> Injects data into the template. Result is a hybrid HTML/Markdown string.</li>
 * <li><strong>Render (Flexmark):</strong> Converts the hybrid string into pure HTML.</li>
 * <li><strong>Assembly (Jsoup):</strong> Injects CSS/Headers and forces strict XHTML compliance.</li>
 * <li><strong>Output (Flying Saucer):</strong> Generates the PDF binary.</li>
 * </ol>
 * </p>
 */
@Service
public class MarkdownService {
    private final Handlebars handlebars;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    /**
     * Initializes the service with a "Trusting" configuration for Flexmark.
     * <p>
     * <strong>Why Trusting?</strong> Since we run Flexmark <em>after</em> Handlebars,
     * the input string will contain valid HTML tags (divs, spans) defined in the template.
     * We must configure Flexmark to <strong>not escape</strong> these tags
     * ({@code SUPPRESS_HTML = false}).
     * </p>
     */
    public MarkdownService() {
        this.handlebars = new Handlebars();

        // FlexMark Configuration
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                AttributesExtension.create()
        ));

        // CRITICAL: Allow HTML tags found in the source to pass through to the output.
        // Without this, your <div class="card"> would become &lt;div class="card"&gt;
        options.set(HtmlRenderer.SUPPRESS_HTML, false);

        // 2. ENABLE DEEP PARSING (Critical for parsing markdown inside HTML tags)
        options.set(Parser.HTML_BLOCK_DEEP_PARSER, true);
        options.set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false);

        // 3. IGNORE INDENTATION (Critical to stop 4 spaces from becoming Code Blocks)
        // Note: This disables ALL indented code blocks in your markdown.
        // If you need code blocks, use triple backticks ``` instead.
        options.set(Parser.INDENTED_CODE_BLOCK_PARSER, false);

        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Executes the generation pipeline.
     *
     * @param request The DTO containing Base64 encoded resources and the dynamic data map.
     * @return An {@link InputStreamResource} containing the generated PDF binary.
     * @throws RuntimeException If Handlebars compilation fails or PDF rendering encounters an error.
     * @throws IllegalArgumentException If Base64 decoding fails.
     */
    public Resource generateDocument(GenerateRequestDto request) {
        try {
            // Step 1: Decode Inputs
            String templateStr = decode(request.getTemplateEncoded());
            String cssStr = decode(request.getCssEncoded());
            String headerStr = decode(request.getHeaderEncoded()); // Changed
            String footerStr = decode(request.getFooterEncoded()); // Changed
            Map<String, Object> rawData = request.getDocPropertiesJsonData();

            // Step 2: Input Sanitization (Security Layer)
            // We scrub the input data BEFORE it touches the template to prevent XSS.
            sanitizeInputData(rawData);

            // Step 3: Handlebars Merge (The "Hybrid" State)
            // We apply data to the template. The result is a mix of HTML (from template)
            // and Markdown (from data or loop structures).
            Template template = handlebars.compileInline(templateStr);
            String hybridContent = template.apply(rawData);

            // Step 4: Markdown Conversion
            // Parse the hybrid content, find <md> blocks, and convert them to HTML
            Document doc = processMarkdownToDom(hybridContent);

            // Step 5: DOM Assembly & XHTML Normalization
            // Prepares valid XML for the strict PDF renderer
            configureFinalDom(doc, cssStr, headerStr, footerStr);
            String finalXHtml = doc.html();

            // Step 6: PDF Generation via Flying Saucer
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ITextRenderer renderer = new ITextRenderer();

                // ---------------------------------------------------------------------
                // CUSTOM USER AGENT CONFIGURATION
                // ---------------------------------------------------------------------
                // We typically use NaiveUserAgent for Swing, but for PDF generation we MUST
                // use ITextUserAgent. Failing to do so causes a ClassCastException because
                // the PDF renderer expects ITextFSImage objects, not AWT objects.
                ITextUserAgent callback = new ITextUserAgent(renderer.getOutputDevice()) {
                    @Override
                    public byte[] getBinaryResource(String uri) {
                        // SECURITY: SSRF Protection
                        // Block the renderer from fetching external URLs to prevent leaking internal data.
                        if (uri.startsWith("http://") || uri.startsWith("https://")) {
                            System.out.println("Blocked external resource request: " + uri);
                            return new byte[0]; // Return empty image
                        }

                        // FEATURE: Local Classpath Loading
                        // Allows templates to reference images like: <img src="classpath:static/logo.png" />
                        if (uri.startsWith("classpath:")) {
                            return loadFromClasspath(uri);
                        }

                        // Fallback for standard relative paths (if applicable)
                        return super.getBinaryResource(uri);
                    }
                };

                // Link the custom agent to the renderer's context
                callback.setSharedContext(renderer.getSharedContext());
                renderer.getSharedContext().setUserAgentCallback(callback);

                renderer.setDocumentFromString(finalXHtml);
                renderer.layout();
                renderer.createPDF(outputStream);
                renderer.finishPDF();

                return new InputStreamResource(
                        new ByteArrayInputStream(outputStream.toByteArray())
                );

            }
        } catch (IOException e) {
            throw new RuntimeException("Error during template compilation or IO", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 input provided", e);
        }
    }

    /**
     * Helper to load bytes from the resources folder.
     * Extracts path from "classpath:path/to/file.png".
     */
    private byte[] loadFromClasspath(String uri) {
        try {
            String path = uri.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(path);

            if (!resource.exists()) {
                System.err.println("Resource not found: " + path);
                return new byte[0];
            }

            try (InputStream is = resource.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            System.err.println("Error loading classpath resource: " + uri);
            return new byte[0];
        }
    }
    /**
     * Recursively scrubs unsafe HTML from the input data map.
     * <p>
     * <strong>Why this is needed:</strong> Since we allow HTML to pass through Flexmark later
     * (to support the template's layout), we must ensure the <em>user-provided data</em>
     * does not contain malicious scripts or broken tags.
     * </p>
     *
     * @param data The mutable map representing the JSON payload.
     */
    @SuppressWarnings("unchecked")
    private void sanitizeInputData(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String) {
                // Safelist.none() strips ALL HTML tags.
                // Change to Safelist.simpleText() if you want to allow <b> or <i> in user data.
                String safeValue = Jsoup.clean((String) value, Safelist.none());
                entry.setValue(safeValue);
            } else if (value instanceof Map) {
                sanitizeInputData((Map<String, Object>) value);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map) {
                        sanitizeInputData((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    /**
     * Utility method to decode Base64 strings to UTF-8.
     * @param base64 The encoded string.
     * @return The decoded string, or empty string if input is null.
     */
    private String decode(String base64) {
        if (base64 == null) return "";
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    /**
     * Parses the hybrid content, finds <md> tags, converts them to HTML, and modifies the DOM in-place.
     * <p>
     * This DOM-based approach is more efficient than regex-based processing and preserves document structure.
     * </p>
     */
    private Document processMarkdownToDom(String hybridContent) {
        // Parse the full document immediately
        Document doc = Jsoup.parse(hybridContent);

        // Find all <md> tags in the document
        Elements mdBlocks = doc.select("md");

        for (Element mdElement : mdBlocks) {
            // CRITICAL: Use wholeText() to preserve newlines for Markdown parsing.
            // .text() would strip newlines and merge lines, breaking lists/tables.
            String markdownText = mdElement.wholeText();

            // Strip leading whitespace from each line to handle template indentation
            String cleanMarkdown = markdownText.lines()
                    .map(String::trim)
                    .reduce((line1, line2) -> line1 + "\n" + line2)
                    .orElse("");

            // Render Markdown to HTML
            String renderedHtml = htmlRenderer.render(markdownParser.parse(cleanMarkdown));

            // Replace the <md> element with the rendered HTML
            mdElement.after(renderedHtml);
            mdElement.remove();
        }

        return doc;
    }

    /**
     * Configures the final DOM with CSS, header, and footer, enforcing strict XHTML compliance.
     * <p>
     * Flying Saucer requires valid XML (XHTML). This method ensures all tags are properly closed
     * and entities are correctly escaped.
     * </p>
     *
     * @param doc The document to configure
     * @param cssStr The raw CSS string
     * @param headerStr The raw HTML string containing header elements
     * @param footerStr The raw HTML string containing footer elements
     */
    private void configureFinalDom(Document doc, String cssStr, String headerStr, String footerStr) {
        // Enforce XHTML Syntax for Flying Saucer
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
        doc.charset(StandardCharsets.UTF_8);

        // Inject CSS
        if (cssStr != null && !cssStr.isBlank()) {
            Element style = doc.head().appendElement("style");
            style.append("<![CDATA[\n" + cssStr + "\n]]>");
        }

        // Inject Footer (prepended first so header appears on top)
        if (footerStr != null && !footerStr.isBlank()) {
            doc.body().prepend(footerStr);
        }

        // Inject Header (prepended after footer to appear at the very top)
        if (headerStr != null && !headerStr.isBlank()) {
            doc.body().prepend(headerStr);
        }
    }
}