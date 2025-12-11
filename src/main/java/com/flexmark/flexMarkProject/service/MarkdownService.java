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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final Logger logger = LoggerFactory.getLogger(MarkdownService.class);
    
    // Constants for image processing
    private static final String DATA_URI_PREFIX = "data:";
    private static final String DATA_URI_SEPARATOR = ",";
    private static final String MIME_TYPE_SEPARATOR = ";";
    private static final String BASE64_ENCODING = "base64";
    
    // Supported MIME types
    private static final String MIME_TYPE_PNG = "image/png";
    private static final String MIME_TYPE_JPEG = "image/jpeg";
    private static final String MIME_TYPE_JPG = "image/jpg";
    private static final String MIME_TYPE_SVG = "image/svg+xml";
    
    // Magic bytes for image format detection
    private static final byte[] PNG_SIGNATURE = {(byte)0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_SIGNATURE = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    
    // Supported MIME types set for fast lookup
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            MIME_TYPE_PNG, MIME_TYPE_JPEG, MIME_TYPE_JPG, MIME_TYPE_SVG
    );
    
    // Static resource paths
    private static final String STATIC_RESOURCE_PATH = "static/";
    private static final String CLASSPATH_STATIC_PREFIX = "classpath:/static/";
    
    // HTML/XML prefixes for detection
    private static final String HTML_PREFIX = "<html";
    private static final String DOCTYPE_PREFIX = "<!DOCTYPE";
    private static final String SVG_MARKER = "<svg";
    private static final String XML_PREFIX = "<?xml";
    
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
            String imageEncoded = decodeImage(request.getImageEncoded());
            Map<String, Object> rawData = request.getDocPropertiesJsonData();
            
            if (rawData == null) {
                logger.warn("No document properties data provided, using empty map");
                rawData = Map.of();
            }

            // Step 2: Process HTML content (header, footer) with custom image if provided
            if (StringUtils.hasText(imageEncoded)) {
                if (StringUtils.hasText(headerStr)) {
                    headerStr = processHtmlWithImage(headerStr, imageEncoded);
                }
                if (StringUtils.hasText(footerStr)) {
                    footerStr = processHtmlWithImage(footerStr, imageEncoded);
                }
            }

            // Step 3: Input Sanitization (Security Layer)
            // We scrub the input data BEFORE it touches the template to prevent XSS.
            // NOTE: Currently disabled - uncomment if you need XSS protection for user data
            // sanitizeInputData(rawData);

            // Step 4: Handlebars Merge (The "Hybrid" State)
            Template template = handlebars.compileInline(templateStr);
            String hybridContent = template.apply(rawData);
            logger.debug("Handlebars template applied successfully");

            // Step 5: Markdown Conversion
            Document doc = processMarkdownToDom(hybridContent);
            logger.debug("Markdown converted to DOM");

            // Step 6: DOM Assembly & XHTML Normalization
            configureFinalDom(doc, cssStr, headerStr, footerStr, imageEncoded);

            // Step 7: PDF Generation via iText7 html2pdf
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
     *
     * @return Configured ConverterProperties instance
     */
    private ConverterProperties createConverterProperties() {
        ConverterProperties properties = new ConverterProperties();
        
        // FontProvider for managing fonts
        FontProvider fontProvider = new DefaultFontProvider(false, true, false);
        properties.setFontProvider(fontProvider);

        // Get the real URL to the "static" folder from the ClassLoader
        java.net.URL staticUrl = getClass().getClassLoader().getResource(STATIC_RESOURCE_PATH);

        // Set the Base URI
        if (staticUrl != null) {
            properties.setBaseUri(staticUrl.toString());
            logger.debug("Using static resource base URI: {}", staticUrl);
        } else {
            properties.setBaseUri(CLASSPATH_STATIC_PREFIX);
            logger.warn("Static resource folder not found, using fallback: {}", CLASSPATH_STATIC_PREFIX);
        }

        return properties;
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
     * Decodes a Base64-encoded image string, consistent with other DTO parameters.
     * <p>
     * Handles three scenarios:
     * <ol>
     * <li>If it's a data URI (starts with "data:"), returns as-is</li>
     * <li>If it's Base64-encoded (like other DTO fields), decodes it to get the Base64 image data</li>
     * <li>If it's already raw Base64 image data, returns as-is</li>
     * </ol>
     * </p>
     * 
     * @param imageEncoded The Base64-encoded image string (may be null)
     * @return The Base64 image data string ready for use in data URI, or empty string if input is null
     */
    private String decodeImage(String imageEncoded) {
        if (!StringUtils.hasText(imageEncoded)) {
            return "";
        }
        
        // If it's already a data URI, return as-is
        if (imageEncoded.startsWith(DATA_URI_PREFIX)) {
            return imageEncoded;
        }
        
        try {
            // Try to decode it (consistent with other DTO parameters)
            String decoded = new String(Base64.getDecoder().decode(imageEncoded), StandardCharsets.UTF_8);
            
            // If decoded result looks like Base64 image data or is a data URI, use the decoded version
            if (decoded.startsWith(DATA_URI_PREFIX) || decoded.matches("^[A-Za-z0-9+/=]+$")) {
                return decoded;
            }
            
            // If decoding produced something that doesn't look like Base64,
            // the original was likely already raw Base64 image data
            return imageEncoded;
        } catch (IllegalArgumentException e) {
            logger.debug("Image string is not Base64-encoded, treating as raw Base64 data");
            // If decoding fails, assume it's already raw Base64 image data
            return imageEncoded;
        }
    }

    /**
     * Processes HTML content (template, header, footer) to replace image references 
     * with a Base64 data URI if an encoded image is provided.
     * <p>
     * This method is class-agnostic and works with any HTML string. If an encoded image 
     * is provided, this method:
     * <ol>
     * <li>Parses the HTML using Jsoup</li>
     * <li>Finds all img tags</li>
     * <li>Validates the image type (only .png, .jpg, .svg allowed)</li>
     * <li>Replaces their src attributes with a data URI containing the decoded image</li>
     * </ol>
     * If no encoded image is provided or validation fails, the HTML remains unchanged 
     * and will use the default static image from resources/static.
     * </p>
     *
     * @param htmlStr The HTML string (template, header, or footer)
     * @param imageEncoded Base64 encoded image string (may include data URI prefix)
     * @return The processed HTML with image src replaced, or original if processing/validation fails
     */
    private String processHtmlWithImage(String htmlStr, String imageEncoded) {
        try {
            String dataUri = createDataUri(imageEncoded);
            if (dataUri == null) {
                logger.debug("Image validation failed, returning original HTML");
                return htmlStr;
            }
            
            Document htmlDoc = Jsoup.parse(htmlStr);
            Elements imgTags = htmlDoc.select("img");
            
            if (imgTags.isEmpty()) {
                return htmlStr;
            }
            
            // Replace src attribute for all img tags
            imgTags.forEach(img -> img.attr("src", dataUri));
            
            // Return the processed HTML
            String trimmed = htmlStr.trim();
            if (trimmed.startsWith(HTML_PREFIX) || trimmed.startsWith(DOCTYPE_PREFIX)) {
                return htmlDoc.html();
            } else {
                return htmlDoc.body().html();
            }
        } catch (Exception e) {
            logger.warn("Failed to process HTML with image, returning original HTML", e);
            return htmlStr;
        }
    }
    
    /**
     * Creates a data URI from an encoded image string if valid.
     *
     * @param imageEncoded Base64 encoded image string
     * @return Data URI string, or null if image is invalid or unsupported
     */
    private String createDataUri(String imageEncoded) {
        if (!StringUtils.hasText(imageEncoded)) {
            return null;
        }
        
        String mimeType = determineImageMimeType(imageEncoded);
        if (!isSupportedImageType(mimeType)) {
            return null;
        }
        
        String base64Data = extractBase64Data(imageEncoded);
        return DATA_URI_PREFIX + mimeType + MIME_TYPE_SEPARATOR + BASE64_ENCODING + DATA_URI_SEPARATOR + base64Data;
    }

    /**
     * Validates that the image MIME type is supported.
     * Only .png, .jpg/.jpeg, and .svg are allowed.
     *
     * @param mimeType The MIME type to validate
     * @return true if the image type is supported, false otherwise
     */
    private boolean isSupportedImageType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return false;
        }
        String normalized = mimeType.toLowerCase().trim();
        // Normalize jpg to jpeg for consistency
        if (normalized.equals(MIME_TYPE_JPG)) {
            normalized = MIME_TYPE_JPEG;
        }
        return SUPPORTED_MIME_TYPES.contains(normalized);
    }

    /**
     * Determines the MIME type of an image from its Base64 encoded string.
     * Only supports .png, .jpg/.jpeg, and .svg formats.
     * Checks for data URI prefix or attempts to detect from the first few bytes.
     *
     * @param imageEncoded The Base64 encoded image string
     * @return The MIME type (e.g., "image/png", "image/jpeg", "image/svg+xml")
     */
    private String determineImageMimeType(String imageEncoded) {
        if (!StringUtils.hasText(imageEncoded)) {
            return MIME_TYPE_PNG; // Default
        }
        
        // Check if it's already a data URI
        if (imageEncoded.startsWith(DATA_URI_PREFIX)) {
            int semicolonIndex = imageEncoded.indexOf(MIME_TYPE_SEPARATOR);
            if (semicolonIndex > 0) {
                String mimeType = imageEncoded.substring(DATA_URI_PREFIX.length(), semicolonIndex);
                // Normalize jpg to jpeg
                if (MIME_TYPE_JPG.equals(mimeType)) {
                    return MIME_TYPE_JPEG;
                }
                return mimeType;
            }
        }
        
        // Try to detect from Base64 data
        try {
            String base64Data = extractBase64Data(imageEncoded);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // Check magic bytes for supported image formats
            if (imageBytes.length >= PNG_SIGNATURE.length && 
                matchesSignature(imageBytes, PNG_SIGNATURE)) {
                return MIME_TYPE_PNG;
            }
            
            if (imageBytes.length >= JPEG_SIGNATURE.length && 
                matchesSignature(imageBytes, JPEG_SIGNATURE)) {
                return MIME_TYPE_JPEG;
            }
            
            // SVG detection: SVG is XML-based, check for SVG markers
            String decodedString = new String(imageBytes, StandardCharsets.UTF_8);
            String trimmed = decodedString.trim();
            if (trimmed.startsWith(XML_PREFIX) || trimmed.startsWith(SVG_MARKER) || 
                (trimmed.startsWith("<") && trimmed.contains(SVG_MARKER))) {
                return MIME_TYPE_SVG;
            }
        } catch (Exception e) {
            logger.debug("Failed to detect image MIME type from bytes, defaulting to PNG", e);
        }
        
        return MIME_TYPE_PNG; // Default fallback
    }
    
    /**
     * Checks if the image bytes match a given signature.
     *
     * @param imageBytes The image byte array
     * @param signature The signature to match against
     * @return true if the signature matches
     */
    private boolean matchesSignature(byte[] imageBytes, byte[] signature) {
        if (imageBytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (imageBytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the Base64 data portion from an encoded image string.
     * Removes data URI prefix if present.
     *
     * @param imageEncoded The Base64 encoded image string (may include data URI prefix)
     * @return The Base64 data string without the prefix
     */
    private String extractBase64Data(String imageEncoded) {
        if (!StringUtils.hasText(imageEncoded)) {
            return imageEncoded;
        }
        
        // If it's a data URI, extract the base64 part after the comma
        if (imageEncoded.startsWith(DATA_URI_PREFIX)) {
            int commaIndex = imageEncoded.indexOf(DATA_URI_SEPARATOR);
            if (commaIndex > 0 && commaIndex < imageEncoded.length() - 1) {
                return imageEncoded.substring(commaIndex + 1);
            }
        }
        
        // Otherwise, assume it's already just the base64 data
        return imageEncoded;
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
            // More efficient than reduce for large markdown blocks
            String cleanMarkdown = markdownText.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
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
     * Flying Saucer requires valid XML (XHTML). This method ensures all tags are properly closed
     * and entities are correctly escaped. Also processes images in the final DOM if an encoded
     * image is provided.
     * </p>
     *
     * @param doc The document to configure
     * @param cssStr The raw CSS string
     * @param headerStr The raw HTML string containing header elements
     * @param footerStr The raw HTML string containing footer elements
     * @param imageEncoded Base64 encoded image string (may be null)
     */
    private void configureFinalDom(Document doc, String cssStr, String headerStr, String footerStr, String imageEncoded) {
        // Enforce XHTML Syntax for Flying Saucer
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
        doc.charset(StandardCharsets.UTF_8);

        // Inject CSS
        if (StringUtils.hasText(cssStr)) {
            Element style = doc.head().appendElement("style");
            style.html(cssStr);
        }

        // Inject Footer (prepended first so header appears on top)
        if (StringUtils.hasText(footerStr)) {
            doc.body().prepend(footerStr);
        }

        // Inject Header (prepended after footer to appear at the very top)
        if (StringUtils.hasText(headerStr)) {
            doc.body().prepend(headerStr);
        }

        // Process images in the final DOM (catches any images in template content after Handlebars processing)
        if (StringUtils.hasText(imageEncoded)) {
            processImagesInDocument(doc, imageEncoded);
        }
    }

    /**
     * Processes all images in a Jsoup Document, replacing their src attributes with a data URI
     * if an encoded image is provided and the image type is supported.
     *
     * @param doc The Jsoup Document to process
     * @param imageEncoded Base64 encoded image string
     */
    private void processImagesInDocument(Document doc, String imageEncoded) {
        try {
            String dataUri = createDataUri(imageEncoded);
            if (dataUri == null) {
                logger.debug("Image validation failed, skipping document image processing");
                return;
            }

            Elements imgTags = doc.select("img");
            if (imgTags.isEmpty()) {
                return;
            }

            // Replace src attribute for all img tags
            imgTags.forEach(img -> img.attr("src", dataUri));
            logger.debug("Processed {} image(s) in document", imgTags.size());
        } catch (Exception e) {
            logger.warn("Failed to process images in document, will use static images", e);
        }
    }
}