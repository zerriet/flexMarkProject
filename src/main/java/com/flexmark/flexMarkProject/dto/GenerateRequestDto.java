package com.flexmark.flexMarkProject.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Data Transfer Object for document generation requests.
 * <p>
 * Contains Base64-encoded resources and dynamic data for the generation pipeline.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Request object for PDF document generation with Handlebars templating and Markdown support",
    example = """
        {
          "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMge3tuYW1lfX08L21kPjwvZGl2Pg==",
          "cssEncoded": "aDEgeyBjb2xvcjogYmx1ZTsgfQ==",
          "headerEncoded": "PGRpdj48aW1nIHNyYz0iZGF0YTppbWFnZS9wbmc7YmFzZTY0LGlWQk9SdzBLR2dvQS4uLiIgLz48L2Rpdj4=",
          "footerEncoded": "PGRpdj5Gb290ZXIgQ29udGVudDwvZGl2Pg==",
          "docPropertiesJsonData": {
            "title": "Welcome Report",
            "name": "Alice",
            "items": [
              {"product": "Widget", "price": 99.99},
              {"product": "Gadget", "price": 149.99}
            ]
          }
        }
        """
)
public class GenerateRequestDto implements Serializable{

    /**
     * Base64-encoded HTML template with Handlebars syntax.
     * This field is required and cannot be blank.
     */
    @NotBlank(message = "Template is required and cannot be empty")
    @Schema(
        description = "Base64-encoded HTML template with Handlebars syntax. Supports Handlebars variables ({{variable}}), loops ({{#each}}), conditionals ({{#if}}), and custom <md> tags for Markdown content.",
        example = "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMge3tuYW1lfX08L21kPjwvZGl2Pg==",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String templateEncoded;

    /**
     * Base64-encoded CSS styles (optional).
     */
    @Schema(
        description = "Base64-encoded CSS styles to be injected into the document head. Supports modern CSS including Flexbox and Grid layouts.",
        example = "aDEgeyBjb2xvcjogYmx1ZTsgZm9udC1zaXplOiAyNHB4OyB9",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String cssEncoded;

    /**
     * Base64-encoded HTML header (optional).
     * May contain embedded data URI images in img src attributes.
     */
    @Schema(
        description = "Base64-encoded HTML header prepended to document body. May contain images as data URIs (e.g., <img src=\"data:image/png;base64,...\">).",
        example = "PGRpdiBpZD0iaGVhZGVyIj48aW1nIHNyYz0iZGF0YTppbWFnZS9wbmc7YmFzZTY0LGlWQk9SdzBLR2dvQS4uLiIgLz48L2Rpdj4=",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String headerEncoded;

    /**
     * Base64-encoded HTML footer (optional).
     * May contain embedded data URI images in img src attributes.
     */
    @Schema(
        description = "Base64-encoded HTML footer prepended to document body (appears after header). May contain images as data URIs.",
        example = "PGRpdiBpZD0iZm9vdGVyIj5QYWdlIDxzcGFuIGNsYXNzPSJwYWdlLW51bWJlciI+PC9zcGFuPjwvZGl2Pg==",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String footerEncoded;

    /**
     * Dynamic data map for Handlebars templating (optional).
     * Contains server-side trusted data.
     */
    @Schema(
        description = "Dynamic data object for Handlebars template variable substitution. Supports nested objects and arrays for loops. Data is trusted and not sanitized.",
        example = """
            {
              "title": "Sales Report",
              "date": "2025-12-26",
              "items": [
                {"name": "Product A", "quantity": 10, "price": 99.99},
                {"name": "Product B", "quantity": 5, "price": 149.99}
              ],
              "total": 1249.85
            }
            """,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Map<String,Object> docPropertiesJsonData;
}
