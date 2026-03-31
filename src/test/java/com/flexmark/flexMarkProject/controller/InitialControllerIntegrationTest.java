package com.flexmark.flexMarkProject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for InitialController.
 * Tests the complete HTTP request/response cycle for PDF generation.
 */
@SpringBootTest
@ActiveProfiles("poc")
@DisplayName("InitialController Integration Tests")
class InitialControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }

    // ==================== Successful Request Tests ====================

    @Test
    @DisplayName("Should generate PDF from valid request")
    void testSuccessfulPdfGeneration() throws Exception {
        // Given: Valid request
        String template = "<div><h1>Test Document</h1></div>";
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));

        // When: POST to /api/content/submit
        MvcResult result = mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Should return 200 OK
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(header().string("Content-Disposition", containsString("generated_report.pdf")))
            .andReturn();

        // Verify PDF content
        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assert pdfBytes.length > 0 : "PDF should not be empty";

        // Verify PDF magic bytes
        assert pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F'
            : "Response should be a valid PDF";
    }

    @Test
    @DisplayName("Should handle request with all optional fields")
    void testRequestWithAllFields() throws Exception {
        // Given: Complete request with all fields
        String template = "<div class='content'><h1>{{title}}</h1></div>";
        String css = ".content { padding: 20px; }";
        String header = "<div>Header</div>";
        String footer = "<div>Footer</div>";

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));
        request.setCssEncoded(Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8)));
        request.setHeaderEncoded(Base64.getEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8)));
        request.setFooterEncoded(Base64.getEncoder().encodeToString(footer.getBytes(StandardCharsets.UTF_8)));
        request.setDocPropertiesJsonData(Map.of("title", "Integration Test"));

        // When/Then
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Should generate PDF with Markdown content")
    void testPdfWithMarkdown() throws Exception {
        // Given: Template with Markdown
        String template = """
            <div>
                <md>
                # Title

                This is **bold** text.

                - Item 1
                - Item 2
                </md>
            </div>
            """;

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));

        // When/Then
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Should generate PDF with data URI image")
    void testPdfWithDataUriImage() throws Exception {
        // Given: Template with data URI image
        String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String template = String.format(
            "<div><img src='data:image/png;base64,%s' style='width: 50px;' /><p>Image Test</p></div>",
            redPixelBase64
        );

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));

        // When/Then
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ==================== Validation Error Tests ====================

    @Test
    @DisplayName("Should return 400 when template is missing")
    void testMissingTemplate() throws Exception {
        // Given: Request without template
        GenerateRequestDto request = new GenerateRequestDto();

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when template is empty")
    void testEmptyTemplate() throws Exception {
        // Given: Request with empty template
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded("");

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid JSON")
    void testInvalidJson() throws Exception {
        // Given: Invalid JSON
        String invalidJson = "{invalid json}";

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 500 for invalid Base64 template")
    void testInvalidBase64Template() throws Exception {
        // Given: Request with invalid Base64
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded("not-valid-base64!@#$%");

        // When/Then: Should return 500 (service throws IllegalArgumentException for invalid Base64)
        // Note: The exception is expected and indicates the validation is working
        try {
            mockMvc.perform(post("/api/content/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
        } catch (Exception e) {
            // Expected - invalid Base64 causes an exception to be thrown
            // Verify it's the right kind of exception
            assertTrue(e.getMessage().contains("Invalid Base64") ||
                      e.getCause() != null && e.getCause().getMessage().contains("Invalid Base64"),
                "Exception should be related to invalid Base64");
        }
    }

    // ==================== Content Type Tests ====================

    @Test
    @DisplayName("Should reject request without Content-Type")
    void testMissingContentType() throws Exception {
        // Given: Valid request
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(
            "<div>Test</div>".getBytes(StandardCharsets.UTF_8)
        ));

        // When/Then: Should return 415 Unsupported Media Type
        mockMvc.perform(post("/api/content/submit")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Should reject request with wrong Content-Type")
    void testWrongContentType() throws Exception {
        // Given: Valid request
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(
            "<div>Test</div>".getBytes(StandardCharsets.UTF_8)
        ));

        // When/Then: Should return 415 Unsupported Media Type
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.TEXT_PLAIN)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnsupportedMediaType());
    }

    // ==================== Complex Document Tests ====================

    @Test
    @DisplayName("Should generate complex multi-page document")
    void testComplexDocument() throws Exception {
        // Given: Complex template with multiple pages worth of content
        StringBuilder longContent = new StringBuilder("<div>");
        for (int i = 1; i <= 10; i++) {
            longContent.append("<h2>Section ").append(i).append("</h2>");
            longContent.append("<p>").append("Lorem ipsum dolor sit amet. ".repeat(50)).append("</p>");
        }
        longContent.append("</div>");

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(
            longContent.toString().getBytes(StandardCharsets.UTF_8)
        ));

        // When/Then
        MvcResult result = mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

        // Verify PDF is substantial (multi-page document should be larger)
        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assert pdfBytes.length > 5000 : "Multi-page PDF should be larger than 5KB";
    }

    @Test
    @DisplayName("Should handle document with Handlebars iteration")
    void testHandlebarsIteration() throws Exception {
        // Given: Template with loop
        String template = """
            <div>
                <h1>Users List</h1>
                <ul>
                {{#each users}}
                <li>{{name}} - {{email}}</li>
                {{/each}}
                </ul>
            </div>
            """;

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));
        request.setDocPropertiesJsonData(Map.of(
            "users", java.util.List.of(
                Map.of("name", "Alice", "email", "alice@example.com"),
                Map.of("name", "Bob", "email", "bob@example.com"),
                Map.of("name", "Charlie", "email", "charlie@example.com")
            )
        ));

        // When/Then
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Should handle document with table")
    void testMarkdownTable() throws Exception {
        // Given: Template with Markdown table
        String template = """
            <div>
                <md>
                | Name | Age | City |
                |------|-----|------|
                | Alice | 25 | NYC |
                | Bob | 30 | LA |
                | Charlie | 35 | Chicago |
                </md>
            </div>
            """;

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8)));

        // When/Then
        mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ==================== HTTP Method Tests ====================

    @Test
    @DisplayName("Should reject GET requests")
    void testGetNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/content/submit"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should reject PUT requests")
    void testPutNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/content/submit"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should reject DELETE requests")
    void testDeleteNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/content/submit"))
            .andExpect(status().isMethodNotAllowed());
    }
}
