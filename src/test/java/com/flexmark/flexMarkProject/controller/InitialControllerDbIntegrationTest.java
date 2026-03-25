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

/**
 * Integration tests for the DB-driven data injection path in {@link InitialController}.
 * Requires the {@code poc} Spring profile so H2 is available and seed data is loaded.
 */
@SpringBootTest
@ActiveProfiles("poc")
@DisplayName("InitialController DB Integration Tests")
class InitialControllerDbIntegrationTest {

    // Minimal template that renders borrower company name and first schedule row via loops
    private static final String BUSINESS_LOAN_TEMPLATE = """
        <html><body>
          <h1>{{agreementRef}}</h1>
          <p>Borrower: {{borrower.companyName}}</p>
          <p>Principal: {{loan.currency}} {{loan.principalFormatted}}</p>
          {{#each schedule}}
            <p>Instalment {{installmentNo}}: {{instalment}}</p>
          {{/each}}
          <p>Officer: {{bank.officerName}}</p>
        </body></html>
        """;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("documentType alone resolves data from DB and generates a PDF")
    void testSubmitWithDocumentType() throws Exception {
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(base64(BUSINESS_LOAN_TEMPLATE));
        request.setDocumentType("business_loan_report");

        MvcResult result = mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 0, "Response body should not be empty");
        // PDF magic bytes
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    @DisplayName("docPropertiesJsonData takes precedence over documentType when both are present")
    void testInlineDataTakesPrecedenceOverDocumentType() throws Exception {
        String template = "<html><body><h1>{{greeting}}</h1></body></html>";

        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(base64(template));
        request.setDocumentType("business_loan_report");                  // would resolve from DB
        request.setDocPropertiesJsonData(Map.of("greeting", "Hello!")); // takes precedence

        MvcResult result = mockMvc.perform(post("/api/content/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 0, "PDF should be generated using the inline data");
    }

    @Test
    @DisplayName("Unknown documentType causes an error (IllegalArgumentException from resolver)")
    void testUnknownDocumentTypeCausesError() throws Exception {
        GenerateRequestDto request = new GenerateRequestDto();
        request.setTemplateEncoded(base64("<html><body>test</body></html>"));
        request.setDocumentType("nonexistent_type");

        // MockMvc propagates unhandled exceptions as ServletException rather than returning
        // an HTTP 500 response. We verify that the request fails with the expected root cause.
        Exception thrown = assertThrows(Exception.class, () ->
            mockMvc.perform(post("/api/content/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andReturn()
        );

        Throwable rootCause = thrown;
        while (rootCause.getCause() != null) rootCause = rootCause.getCause();
        assertTrue(rootCause instanceof IllegalArgumentException,
            "Expected IllegalArgumentException as root cause, got: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains("nonexistent_type"),
            "Message should mention the unknown type");
    }

    // -------------------------------------------------------------------------

    private String base64(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
