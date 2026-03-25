package com.flexmark.flexMarkProject.controller;

import com.flexmark.flexMarkProject.db.DataContextResolver;
import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import com.flexmark.flexMarkProject.service.MarkdownService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller serving as the entry point for PDF document generation.
 * <p>
 * Maps to the <strong>"User Input" -> "Initial Controller"</strong> flow in the architectural schematic.
 * This class handles the HTTP transport layer and delegates business logic to the {@link MarkdownService}.
 * </p>
 */
@RestController
@RequestMapping("/api/content")
@Tag(
    name = "PDF Document Generation",
    description = "API for generating PDF documents from Handlebars templates with Markdown support. " +
                  "Supports dynamic templating, CSS styling, headers/footers, and embedded images via data URIs."
)
public class InitialController {


    private final MarkdownService markdownService;

    private final DataContextResolver dataContextResolver;

    public InitialController(MarkdownService markdownService, DataContextResolver dataContextResolver) {
        this.markdownService = markdownService;
        this.dataContextResolver = dataContextResolver;
    }

    /**
     * Receives document generation request with Base64-encoded resources,
     * triggers the generation pipeline, and returns the generated PDF.
     *
     * @param data The request DTO containing template, CSS, header, footer, and data map.
     * @return ResponseEntity containing the generated PDF as a Resource.
     */
    @PostMapping("/submit")
    @Operation(
        summary = "Generate PDF document",
        description = """
            Generates a PDF document from a Handlebars template with Markdown support.

            **Pipeline:**
            1. Validates and decodes Base64-encoded inputs (template, CSS, header, footer)
            2. Merges dynamic data into Handlebars template (loops, conditionals, variables)
            3. Renders Markdown blocks (<md> tags) to HTML
            4. Assembles final DOM with CSS, headers, footers
            5. Converts to PDF using iText7 with SSRF protection

            **Features:**
            - Handlebars templating: {{variable}}, {{#each}}, {{#if}}
            - Markdown support: Tables, lists, code blocks (wrap in <md> tags)
            - Modern CSS: Flexbox, Grid layouts
            - Data URI images: Embed images as Base64 (no temp files)
            - XHTML compliance: Automatic enforcement for PDF rendering

            **Security:**
            - Data URIs allowed (decoded on-the-fly)
            - External HTTP/HTTPS requests blocked (SSRF protection)
            - Local file:// resources allowed
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "PDF successfully generated",
            content = @Content(
                mediaType = "application/pdf",
                schema = @Schema(type = "string", format = "binary")
            ),
            headers = {
                @io.swagger.v3.oas.annotations.headers.Header(
                    name = "Content-Type",
                    description = "MIME type of the response",
                    schema = @Schema(type = "string", example = "application/pdf")
                ),
                @io.swagger.v3.oas.annotations.headers.Header(
                    name = "Content-Disposition",
                    description = "Content disposition header for PDF display/download",
                    schema = @Schema(type = "string", example = "inline; filename=generated_report.pdf")
                )
            }
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Invalid input (missing required fields, invalid Base64 encoding)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Missing template",
                    value = """
                        {
                          "timestamp": "2025-12-26T10:30:00",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Template is required and cannot be empty",
                          "path": "/api/content/submit"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error - Processing error (template compilation failure, PDF generation error)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Template compilation error",
                    value = """
                        {
                          "timestamp": "2025-12-26T10:30:00",
                          "status": 500,
                          "error": "Internal Server Error",
                          "message": "Error during template compilation or IO",
                          "path": "/api/content/submit"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<@NotNull Resource> submitContent(
        @Parameter(
            description = "Document generation request containing Base64-encoded template, CSS, headers, footers, and dynamic data",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GenerateRequestDto.class),
                examples = {
                    @ExampleObject(
                        name = "Simple template",
                        summary = "Minimal example with title and name",
                        value = """
                            {
                              "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMge3tuYW1lfX08L21kPjwvZGl2Pg==",
                              "cssEncoded": "aDEgeyBjb2xvcjogYmx1ZTsgfQ==",
                              "docPropertiesJsonData": {
                                "title": "Welcome",
                                "name": "Alice"
                              }
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Template with loops",
                        summary = "Example with Handlebars loops and Markdown tables",
                        value = """
                            {
                              "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT57eyNlYWNoIGl0ZW1zfX08bWQ+fCBQcm9kdWN0IHwgUHJpY2UgfAp8LS0tfC0tLXwKfCB7e25hbWV9fSB8ICR7e3ByaWNlfX0gfDwvbWQ+e3svZWFjaH19PC9kaXY+",
                              "cssEncoded": "Ym9keSB7IGZvbnQtZmFtaWx5OiBBcmlhbDsgfQ==",
                              "docPropertiesJsonData": {
                                "title": "Product List",
                                "items": [
                                  {"name": "Widget", "price": "99.99"},
                                  {"name": "Gadget", "price": "149.99"}
                                ]
                              }
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Full example with header/footer",
                        summary = "Complete example with CSS, header, footer, and data URIs",
                        value = """
                            {
                              "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMgUmVwb3J0IFN1bW1hcnkKLSBUb3RhbDoge3t0b3RhbH19PC9tZD48L2Rpdj4=",
                              "cssEncoded": "aDEgeyBjb2xvcjogIzMzMzsgZm9udC1zaXplOiAyNHB4OyB9",
                              "headerEncoded": "PGRpdiBpZD0iaGVhZGVyIj48aW1nIHNyYz0iZGF0YTppbWFnZS9wbmc7YmFzZTY0LGlWQk9SdzBLR2dvQS4uLiIgLz48L2Rpdj4=",
                              "footerEncoded": "PGRpdiBpZD0iZm9vdGVyIj5QYWdlIDxzcGFuIGNsYXNzPSJwYWdlLW51bWJlciI+PC9zcGFuPjwvZGl2Pg==",
                              "docPropertiesJsonData": {
                                "title": "Sales Report",
                                "total": "$1,249.85"
                              }
                            }
                            """
                    )
                }
            )
        )
        @Valid @RequestBody GenerateRequestDto data
    ) {
        // DB-driven data resolution: populate docPropertiesJsonData from the database
        // when a documentType is specified and no inline JSON data was supplied.
        if (StringUtils.hasText(data.getDocumentType())
                && data.getDocPropertiesJsonData() == null) {
            Map<String, Object> runtimeParams = data.getQueryParams() != null
                ? data.getQueryParams()
                : java.util.Map.of();
            data.setDocPropertiesJsonData(dataContextResolver.resolve(data.getDocumentType(), runtimeParams));
        }

        Resource generatedPdf = markdownService.generateDocument(data);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=generated_report.pdf")
                .body(generatedPdf);
    }

    /**
     * Error response schema for Swagger documentation
     */
    @Schema(description = "Error response object")
    private static class ErrorResponse {
        @Schema(description = "Timestamp of the error", example = "2025-12-26T10:30:00")
        private String timestamp;

        @Schema(description = "HTTP status code", example = "400")
        private int status;

        @Schema(description = "HTTP error message", example = "Bad Request")
        private String error;

        @Schema(description = "Detailed error message", example = "Template is required and cannot be empty")
        private String message;

        @Schema(description = "Request path", example = "/api/content/submit")
        private String path;
    }
}
