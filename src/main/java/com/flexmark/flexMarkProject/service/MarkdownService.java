package com.flexmark.flexMarkProject.service;

import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.styledxmlparser.resolver.resource.IResourceRetriever;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * <li><strong>Decode:</strong> Decodes Base64-encoded inputs (template, CSS, header, footer).</li>
 * <li><strong>Merge (Handlebars):</strong> Injects data into the template. Result is a hybrid HTML/Markdown string.</li>
 * <li><strong>Render (Flexmark):</strong> Converts the hybrid string into pure HTML.</li>
 * <li><strong>Assembly (Jsoup):</strong> Injects CSS/Headers/Footers and forces strict XHTML compliance.</li>
 * <li><strong>Output (iText7):</strong> Generates the PDF binary with secure resource retrieval (data URIs allowed, HTTP/HTTPS blocked).</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Image Handling:</strong>
 * Images are embedded directly in HTML using data URIs (e.g., {@code <img src="data:image/png;base64,...">}).
 * A custom {@link SecureDataUriResourceRetriever} provides SSRF protection by blocking external HTTP/HTTPS
 * requests while allowing data URIs and local classpath resources.
 * </p>
 */
@Service
public class MarkdownService {
    private static final Logger logger = LoggerFactory.getLogger(MarkdownService.class);

    // Static resource paths
    private static final String STATIC_RESOURCE_PATH = "static/";
    private static final String CLASSPATH_STATIC_PREFIX = "classpath:/static/";
    private static final String TEMP_IMAGE_DIR = "temp/images/";

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

        // Register shutdown hook to clean up temp files
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempFiles));
    }

    /**
     * Cleans up temporary image files on application shutdown.
     */
    private void cleanupTempFiles() {
        try {
            Path tempDir = Paths.get(TEMP_IMAGE_DIR);
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
                logger.info("Cleaned up temp directory: {}", tempDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up temp files", e);
        }
    }

    /**
     * Executes the generation pipeline.
     *
     * @param request The DTO containing Base64 encoded resources and the dynamic data map.
     * @return An {@link InputStreamResource} containing the generated PDF binary.
     * @throws IllegalArgumentException If request is null, required fields are missing, or Base64 decoding fails.
     * @throws RuntimeException If Handlebars compilation fails or PDF rendering encounters an error.
     */
    public Resource generateDocument(GenerateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        
        if (!StringUtils.hasText(request.getTemplateEncoded())) {
            throw new IllegalArgumentException("Template is required and cannot be empty");
        }
        
        try {
            logger.debug("Starting document generation pipeline");
            
            // Step 1: Decode Inputs
            String templateStr = decode(request.getTemplateEncoded());
            String cssStr = decode(request.getCssEncoded());
            String headerStr = decode(request.getHeaderEncoded());
            String footerStr = decode(request.getFooterEncoded());
            Map<String, Object> rawData = request.getDocPropertiesJsonData();

            if (rawData == null) {
                logger.warn("No document properties data provided, using empty map");
                rawData = Map.of();
            }

            // Step 2: Handlebars Merge (The "Hybrid" State)
            Template template = handlebars.compileInline(templateStr);
            String hybridContent = template.apply(rawData);
            logger.debug("Handlebars template applied successfully");

            // Step 3: Markdown Conversion
            Document doc = processMarkdownToDom(hybridContent);
            logger.debug("Markdown converted to DOM");

            // Step 4: DOM Assembly & XHTML Normalization
            configureFinalDom(doc, cssStr, headerStr, footerStr);

            // Step 5: PDF Generation via iText7 html2pdf
            return generatePdf(doc);
            
        } catch (IOException e) {
            logger.error("IO error during document generation", e);
            throw new RuntimeException("Error during template compilation or IO", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input provided", e);
            throw new IllegalArgumentException("Invalid Base64 input provided: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during document generation", e);
            throw new RuntimeException("Error during document generation", e);
        }
    }

    /**
     * Generates PDF from the final HTML document.
     *
     * @param doc The final configured HTML document
     * @return An InputStreamResource containing the PDF binary
     * @throws IOException If PDF generation fails
     */
    private Resource generatePdf(Document doc) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ConverterProperties properties = createConverterProperties();
            HtmlConverter.convertToPdf(doc.html(), outputStream, properties);
            
            logger.debug("PDF generated successfully, size: {} bytes", outputStream.size());
            return new InputStreamResource(
                    new ByteArrayInputStream(outputStream.toByteArray())
            );
        }
    }

    /**
     * Creates and configures ConverterProperties for PDF generation.
     * <p>
     * Configures secure resource retrieval that:
     * <ul>
     * <li>Allows file:// URIs for temp images</li>
     * <li>Allows local static resources from classpath</li>
     * <li>Blocks external HTTP/HTTPS requests (SSRF protection)</li>
     * </ul>
     * </p>
     *
     * @return Configured ConverterProperties instance
     */
    private ConverterProperties createConverterProperties() {
        ConverterProperties properties = new ConverterProperties();

        // FontProvider for managing fonts
        FontProvider fontProvider = new DefaultFontProvider(false, true, false);
        properties.setFontProvider(fontProvider);

        // Set the Base URI to current working directory
        // This allows iText7 to resolve relative paths like "temp/images/xyz.jpg"
        Path currentDir = Paths.get("").toAbsolutePath();
        String baseUri = currentDir.toUri().toString();
        logger.debug("Using base URI: {}", baseUri);
        properties.setBaseUri(baseUri);

        // Configure secure resource retrieval with SSRF protection
        properties.setResourceRetriever(new SecureFileResourceRetriever());

        return properties;
    }

    /**
     * Secure Resource Retriever that allows file:// URIs for local temp images,
     * but blocks external HTTP/HTTPS requests to prevent SSRF attacks.
     */
    private static class SecureFileResourceRetriever implements IResourceRetriever {

        @Override
        public InputStream getInputStreamByUrl(java.net.URL url) throws java.io.IOException {
            if (url == null) {
                throw new java.io.IOException("URL cannot be null");
            }

            String urlString = url.toString();

            // Allow file:// URLs (local temp images and static resources)
            if (urlString.startsWith("file://") || urlString.startsWith("jar:file:")) {
                return url.openStream();
            }

            // Block all HTTP/HTTPS requests (SSRF protection)
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                throw new java.io.IOException("External HTTP/HTTPS requests are blocked for security reasons: " + urlString);
            }

            // For other protocols, allow default behavior
            return url.openStream();
        }

        @Override
        public byte[] getByteArrayByUrl(java.net.URL url) throws java.io.IOException {
            try (InputStream is = getInputStreamByUrl(url)) {
                return is.readAllBytes();
            }
        }
    }

    /**
     * Utility method to decode Base64 strings to UTF-8.
     * @param base64 The encoded string.
     * @return The decoded string, or empty string if input is null or blank.
     * @throws IllegalArgumentException If Base64 decoding fails.
     */
    private String decode(String base64) {
        if (!StringUtils.hasText(base64)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode Base64 string", e);
            throw new IllegalArgumentException("Invalid Base64 encoding: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts images from data URIs in HTML content and saves them to temp files.
     * Rewrites img src attributes to reference the saved files instead of data URIs.
     *
     * @param htmlContent The HTML content containing img tags with data URIs
     * @return The modified HTML with file-based image references
     * @throws IOException If file operations fail
     */
    private String extractAndSaveImages(String htmlContent) throws IOException {
        if (!StringUtils.hasText(htmlContent)) {
            return htmlContent;
        }

        // Parse the HTML
        Document doc = Jsoup.parse(htmlContent);
        Elements imgTags = doc.select("img[src^=data:]");

        if (imgTags.isEmpty()) {
            logger.debug("No data URI images found in content");
            return htmlContent;
        }

        logger.debug("Found {} data URI image(s) to extract", imgTags.size());

        // Create temp directory if it doesn't exist
        Path tempDir = Paths.get(TEMP_IMAGE_DIR);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
            logger.debug("Created temp directory: {}", tempDir.toAbsolutePath());
        }

        // Process each image
        for (Element img : imgTags) {
            String dataUri = img.attr("src");

            try {
                // Extract the image data
                ImageData imageData = parseDataUri(dataUri);

                // Generate unique filename
                String filename = UUID.randomUUID() + "." + imageData.extension;
                Path imagePath = tempDir.resolve(filename);

                // Save the image to file
                Files.write(imagePath, imageData.data);
                logger.debug("Saved image to: {}", imagePath.toAbsolutePath());

                // Update the img src to reference the file
                // Use relative path from temp directory
                String relativePath = TEMP_IMAGE_DIR + filename;
                img.attr("src", relativePath);
                logger.debug("Rewrote img src to: {}", relativePath);

            } catch (Exception e) {
                logger.error("Failed to extract image from data URI", e);
                // Leave the original data URI if extraction fails
            }
        }

        // Return the full HTML content (not just body's inner HTML)
        // This preserves the complete structure including wrapper divs
        return doc.body().html();
    }

    /**
     * Parses a data URI and extracts the binary data and file extension.
     *
     * @param dataUri The data URI (e.g., "data:image/png;base64,...")
     * @return ImageData containing the decoded bytes and file extension
     * @throws IOException If the data URI is malformed
     */
    private ImageData parseDataUri(String dataUri) throws IOException {
        // Data URI format: data:[<mediatype>][;base64],<data>
        int commaIndex = dataUri.indexOf(',');
        if (commaIndex < 0 || commaIndex >= dataUri.length() - 1) {
            throw new IOException("Malformed data URI: missing comma separator");
        }

        String metadata = dataUri.substring(5, commaIndex); // Skip "data:"
        String data = dataUri.substring(commaIndex + 1);

        // Extract media type and determine extension
        String extension = "bin"; // default
        if (metadata.contains("image/png")) {
            extension = "png";
        } else if (metadata.contains("image/jpeg") || metadata.contains("image/jpg")) {
            extension = "jpg";
        } else if (metadata.contains("image/gif")) {
            extension = "gif";
        } else if (metadata.contains("image/webp")) {
            extension = "webp";
        } else if (metadata.contains("image/svg+xml")) {
            extension = "svg";
        }

        // Decode the data
        byte[] decodedData;
        if (metadata.contains("base64")) {
            decodedData = Base64.getDecoder().decode(data);
        } else {
            // URL-encoded data (less common)
            decodedData = java.net.URLDecoder.decode(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }

        return new ImageData(decodedData, extension);
    }

    /**
     * Helper class to hold extracted image data and extension.
     */
    private static class ImageData {
        final byte[] data;
        final String extension;

        ImageData(byte[] data, String extension) {
            this.data = data;
            this.extension = extension;
        }
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
            // IMPORTANT: We preserve blank lines as they're significant in Markdown for paragraph separation
            String cleanMarkdown = markdownText.lines()
                    .map(line -> line.trim().isEmpty() ? "" : line.trim())
                    .collect(Collectors.joining("\n"));

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
     * iText7 requires valid XML (XHTML). This method ensures all tags are properly closed
     * and entities are correctly escaped. Images with data URIs are handled natively by iText7.
     * </p>
     * <p>
     * Headers and footers are parsed through Jsoup to ensure img tags with data URIs are
     * properly handled and not corrupted during DOM manipulation.
     * </p>
     *
     * @param doc The document to configure
     * @param cssStr The raw CSS string
     * @param headerStr The raw HTML string containing header elements (may include data URI images)
     * @param footerStr The raw HTML string containing footer elements (may include data URI images)
     */
    private void configureFinalDom(Document doc, String cssStr, String headerStr, String footerStr) {
        // Enforce XHTML Syntax for iText7
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
        doc.charset(StandardCharsets.UTF_8);

        // Inject CSS
        if (StringUtils.hasText(cssStr)) {
            Element style = doc.head().appendElement("style");
            style.html(cssStr);
        }

        // Inject Footer (prepended first so header appears on top)
        // Extract data URI images and save to temp files
        if (StringUtils.hasText(footerStr)) {
            logger.debug("Processing footer - HTML length: {}", footerStr.length());
            logger.debug("Footer contains data URI: {}", footerStr.contains("data:image"));

            try {
                // Extract images from data URIs and save to temp files
                String processedFooter = extractAndSaveImages(footerStr);

                // Parse footer as a separate document to ensure proper handling
                Document footerDoc = Jsoup.parse(processedFooter);
                footerDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
                footerDoc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

                // Extract the body content from the parsed footer
                String parsedFooter = footerDoc.body().html();
                doc.body().prepend(parsedFooter);

                logger.debug("Footer processed successfully");
            } catch (IOException e) {
                logger.error("Failed to process footer images, using original footer", e);
                doc.body().prepend(footerStr);
            }
        }

        // Inject Header (prepended after footer to appear at the very top)
        // Extract data URI images and save to temp files
        if (StringUtils.hasText(headerStr)) {
            logger.debug("Processing header - HTML length: {}", headerStr.length());
            logger.debug("Header contains data URI: {}", headerStr.contains("data:image"));

            try {
                // Extract images from data URIs and save to temp files
                String processedHeader = extractAndSaveImages(headerStr);

                // Parse header as a separate document to ensure proper handling
                Document headerDoc = Jsoup.parse(processedHeader);
                headerDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
                headerDoc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

                // Extract the body content from the parsed header
                String parsedHeader = headerDoc.body().html();
                doc.body().prepend(parsedHeader);

                logger.debug("Header processed successfully");
            } catch (IOException e) {
                logger.error("Failed to process header images, using original header", e);
                doc.body().prepend(headerStr);
            }
        }
    }

}