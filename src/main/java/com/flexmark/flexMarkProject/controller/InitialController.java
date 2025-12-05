package com.flexmark.flexMarkProject.controller;

import com.flexmark.flexMarkProject.dto.GenerateRequestDto;
import com.flexmark.flexMarkProject.service.MarkdownService;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller serving as the entry point for user content submission.
 * <p>
 * Maps to the <strong>"User Input" -> "Initial Controller"</strong> flow in the architectural schematic.
 * This class handles the HTTP transport layer and delegates business logic to the {@link MarkdownService}.
 * </p>
 */
@RestController
@RequestMapping("/api/content")
public class InitialController {
    

    private final MarkdownService markdownService;

    /**
     * Constructor Injection for the MarkdownService.
     * * @param markdownService The service responsible for processing and storing content.
     */
    public InitialController(MarkdownService markdownService) {
        this.markdownService = markdownService;
    }

    /**
     * Receives raw Markdown text from the client, triggers the storage process,
     * and returns the converted HTML fragment.
     *
     * @param data The raw Markdown string sent in the request body.
     * @return The processed HTML string (fragment) ready for display or further processing.
     */
    @PostMapping("/submit")
    public ResponseEntity<@NotNull Resource> submitContent(@RequestBody GenerateRequestDto data) {
        Resource generatedPdf = markdownService.generateDocument(data);

        // 2. Return it with specific PDF headers
        return ResponseEntity.ok()
                // Tells the client: "This is a PDF, not text"
                .contentType(MediaType.APPLICATION_PDF)
                // Tells the client: "Open this inside the browser/viewer" (inline)
                // If you want it to auto-download, change "inline" to "attachment"
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=generated_report.pdf")
                .body(generatedPdf);
    }
}
