package com.flexmark.flexMarkProject.service;

import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.layout.font.FontProvider;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

            // Step 6: PDF Generation via iText7 html2pdf
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                // The ConverterProperties object holds configuration for the PDF conversion process.
                ConverterProperties properties = new ConverterProperties();

                // A FontProvider is crucial for managing fonts, especially custom or non-standard ones.
                // DefaultFontProvider is a standard implementation that can be configured to discover
                // fonts from various system locations.
                FontProvider fontProvider = new DefaultFontProvider(false, true, false);
                properties.setFontProvider(fontProvider);

                //Get the real URL to the "static" folder from the ClassLoader
                java.net.URL staticUrl = getClass().getClassLoader().getResource("static/");

                //Set the Base URI
                if (staticUrl != null) {
                    // This converts "classpath:/static/" to a concrete path like:
                    // "file:/C:/Project/target/classes/static/" (Local)
                    // "jar:file:/app.jar!/BOOT-INF/classes/static/" (Docker/Prod)
                    properties.setBaseUri(staticUrl.toString());
                } else {
                    // Fallback (though this usually means the folder is empty or missing)
                    properties.setBaseUri("classpath:/static/");
                }

                // This is the core iText7 conversion call. It takes the final HTML string,
                // a stream to write the PDF to, and the configuration properties.
                HtmlConverter.convertToPdf(doc.html(), outputStream, properties);

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
            style.html(cssStr);
//            style.append("<![CDATA[\n" + cssStr + "\n]]>");
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