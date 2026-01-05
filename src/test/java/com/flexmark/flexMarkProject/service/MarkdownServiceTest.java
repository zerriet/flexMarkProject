package com.flexmark.flexMarkProject.service;

import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarkdownService.
 * Tests the complete document generation pipeline including:
 * - Template processing with Handlebars
 * - Markdown to HTML conversion with Flexmark
 * - Data URI image handling
 * - PDF generation with iText7
 */
@DisplayName("MarkdownService Tests")
class MarkdownServiceTest {

    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    // ==================== Basic Functionality Tests ====================

    @Test
    @DisplayName("Should generate PDF from simple template")
    void testGenerateDocumentWithSimpleTemplate() throws IOException {
        // Given: A simple template with no variables
        String template = "<div><h1>Hello World</h1></div>";
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result, "Generated PDF should not be null");
        assertTrue(result.exists(), "Generated PDF should exist");

        // Verify it's a PDF by checking the magic bytes and size
        try (InputStream is = result.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = is.read(header);
            assertEquals(4, bytesRead, "Should read 4 header bytes");
            assertEquals('%', header[0], "PDF should start with %");
            assertEquals('P', header[1], "PDF should have P as second byte");
            assertEquals('D', header[2], "PDF should have D as third byte");
            assertEquals('F', header[3], "PDF should have F as fourth byte");

            // Read rest of stream to verify there's content
            byte[] rest = is.readAllBytes();
            assertTrue(rest.length > 0, "PDF should have content beyond header");
        }
    }

    @Test
    @DisplayName("Should process Handlebars variables correctly")
    void testHandlebarsVariableSubstitution() throws IOException {
        // Given: A template with Handlebars variables
        String template = "<div><h1>Hello {{name}}</h1><p>Age: {{age}}</p></div>";
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setDocPropertiesJsonData(Map.of(
            "name", "John Doe",
            "age", 30
        ));

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF with substituted values
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should convert Markdown to HTML correctly")
    void testMarkdownConversion() throws IOException {
        // Given: A template with markdown content in <md> tags
        String template = """
            <div>
                <md>
                # Title

                This is **bold** and this is *italic*.

                - List item 1
                - List item 2
                </md>
            </div>
            """;
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should handle Markdown tables correctly")
    void testMarkdownTables() throws IOException {
        // Given: A template with a markdown table
        String template = """
            <div>
                <md>
                | Column 1 | Column 2 |
                |----------|----------|
                | Data 1   | Data 2   |
                | Data 3   | Data 4   |
                </md>
            </div>
            """;
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    // ==================== CSS Injection Tests ====================

    @Test
    @DisplayName("Should inject CSS correctly")
    void testCssInjection() throws IOException {
        // Given: A template with CSS
        String template = "<div class='custom'>Styled Content</div>";
        String css = ".custom { color: red; font-size: 20px; }";

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
        String encodedCss = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setCssEncoded(encodedCss);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    // ==================== Header and Footer Tests ====================

    @Test
    @DisplayName("Should inject header correctly")
    void testHeaderInjection() throws IOException {
        // Given: A template with header
        String template = "<div>Main Content</div>";
        String header = "<div id='header'><h3>Document Header</h3></div>";

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
        String encodedHeader = Base64.getEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setHeaderEncoded(encodedHeader);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should inject footer correctly")
    void testFooterInjection() throws IOException {
        // Given: A template with footer
        String template = "<div>Main Content</div>";
        String footer = "<div id='footer'><p>Page <span class='page-number'></span></p></div>";

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
        String encodedFooter = Base64.getEncoder().encodeToString(footer.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setFooterEncoded(encodedFooter);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should handle footer with data URI image")
    void testFooterWithDataUriImage() throws IOException {
        // Given: A 1x1 red pixel PNG as data URI
        String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String footer = String.format(
            "<div id='footer'><img src='data:image/png;base64,%s' style='height: 20px;' /><p>Footer Text</p></div>",
            redPixelBase64
        );

        String template = "<div>Main Content</div>";
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
        String encodedFooter = Base64.getEncoder().encodeToString(footer.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setFooterEncoded(encodedFooter);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF with image
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    // ==================== Complex Integration Tests ====================

    @Test
    @DisplayName("Should handle complete document with all features")
    void testCompleteDocument() throws IOException {
        // Given: A complete document with template, CSS, header, footer, and data
        String template = """
            <div class="container">
                <h1>{{title}}</h1>
                <md>
                ## Introduction

                Welcome to the **{{companyName}}** report.

                | Metric | Value |
                |--------|-------|
                | Revenue | {{revenue}} |
                | Profit | {{profit}} |
                </md>
            </div>
            """;

        String css = """
            .container { padding: 20px; }
            h1 { color: navy; }
            table { border-collapse: collapse; width: 100%; }
            td, th { border: 1px solid #ddd; padding: 8px; }
            """;

        String header = "<div style='text-align: center; border-bottom: 2px solid navy;'><h3>Annual Report</h3></div>";
        String footer = "<div style='text-align: center; border-top: 1px solid gray;'><p>Page <span class='page-number'></span></p></div>";

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));
        request.setCssEncoded(Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8)));
        request.setHeaderEncoded(Base64.getEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8)));
        request.setFooterEncoded(Base64.getEncoder().encodeToString(footer.getBytes(StandardCharsets.UTF_8)));
        request.setDocPropertiesJsonData(Map.of(
            "title", "Q4 2024 Report",
            "companyName", "Acme Corp",
            "revenue", "$1.2M",
            "profit", "$350K"
        ));

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);

        // Verify PDF has substantial content (read the entire stream once)
        try (InputStream is = result.getInputStream()) {
            byte[] pdfBytes = is.readAllBytes();
            assertTrue(pdfBytes.length > 1000, "PDF should contain substantial content (>1KB)");

            // Verify PDF header
            assertEquals('%', pdfBytes[0], "PDF should start with %");
            assertEquals('P', pdfBytes[1]);
            assertEquals('D', pdfBytes[2]);
            assertEquals('F', pdfBytes[3]);
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw exception when request is null")
    void testNullRequest() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
            markdownService.generateDocument(null)
        );
    }

    @Test
    @DisplayName("Should throw exception when template is missing")
    void testMissingTemplate() {
        // Given: Request without template
        GenerateRequestDto request = new GenerateRequestDto();

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
            markdownService.generateDocument(request)
        );
    }

    @Test
    @DisplayName("Should throw exception when template is empty")
    void testEmptyTemplate() {
        // Given: Request with empty template
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded("");

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
            markdownService.generateDocument(request)
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid Base64 encoding")
    void testInvalidBase64() {
        // Given: Request with invalid Base64
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded("not-valid-base64!@#$");

        // When/Then: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
            markdownService.generateDocument(request)
        );
    }

    @Test
    @DisplayName("Should handle empty data map gracefully")
    void testEmptyDataMap() throws IOException {
        // Given: Template with variables but empty data map
        String template = "<div>Hello {{name}}</div>";
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setDocPropertiesJsonData(Map.of());

        // When: Generating the document (should not crash)
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF (with empty variable substitutions)
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should handle null data map gracefully")
    void testNullDataMap() throws IOException {
        // Given: Template with variables but null data map
        String template = "<div>Hello {{name}}</div>";
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setDocPropertiesJsonData(null);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    // ==================== Data URI Handling Tests ====================

    @Test
    @DisplayName("Should handle multiple data URI images")
    void testMultipleDataUriImages() throws IOException {
        // Given: Template with multiple data URI images
        String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

        String template = String.format("""
            <div>
                <img src='data:image/png;base64,%s' style='width: 10px;' />
                <img src='data:image/png;base64,%s' style='width: 10px;' />
                <img src='data:image/png;base64,%s' style='width: 10px;' />
            </div>
            """, redPixelBase64, redPixelBase64, redPixelBase64);

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF with all images
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should handle JPEG data URI")
    void testJpegDataUri() throws IOException {
        // Given: A minimal JPEG data URI (black 1x1 pixel)
        String jpegBase64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k=";

        String template = String.format("<div><img src='data:image/jpeg;base64,%s' /></div>", jpegBase64);
        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    // ==================== Handlebars Loop Tests ====================

    @Test
    @DisplayName("Should handle Handlebars loops correctly")
    void testHandlebarsLoops() throws IOException {
        // Given: Template with Handlebars loop
        String template = """
            <div>
                <md>
                | Name | Age |
                |------|-----|
                {{#each users}}
                | {{name}} | {{age}} |
                {{/each}}
                </md>
            </div>
            """;

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setDocPropertiesJsonData(Map.of(
            "users", java.util.List.of(
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 30),
                Map.of("name", "Charlie", "age", 35)
            )
        ));

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF with all rows
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }

    @Test
    @DisplayName("Should handle nested Handlebars structures")
    void testNestedHandlebars() throws IOException {
        // Given: Template with nested Handlebars
        String template = """
            <div>
                {{#each departments}}
                <h2>{{name}}</h2>
                <ul>
                {{#each employees}}
                <li>{{name}} - {{role}}</li>
                {{/each}}
                </ul>
                {{/each}}
            </div>
            """;

        String encodedTemplate = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(encodedTemplate);
        request.setDocPropertiesJsonData(Map.of(
            "departments", java.util.List.of(
                Map.of(
                    "name", "Engineering",
                    "employees", java.util.List.of(
                        Map.of("name", "Alice", "role", "Developer"),
                        Map.of("name", "Bob", "role", "Architect")
                    )
                ),
                Map.of(
                    "name", "Sales",
                    "employees", java.util.List.of(
                        Map.of("name", "Charlie", "role", "Manager")
                    )
                )
            )
        ));

        // When: Generating the document
        Resource result = markdownService.generateDocument(request);

        // Then: Should produce a valid PDF
        assertNotNull(result);
        assertTrue(result.contentLength() > 0);
    }
}
