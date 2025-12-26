package com.flexmark.flexMarkProject.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class GenerateRequestDto implements Serializable{

    /**
     * Base64-encoded HTML template with Handlebars syntax.
     * This field is required and cannot be blank.
     */
    @NotBlank(message = "Template is required and cannot be empty")
    private String templateEncoded;

    /**
     * Base64-encoded CSS styles (optional).
     */
    private String cssEncoded;

    /**
     * Base64-encoded HTML header (optional).
     * May contain embedded data URI images in img src attributes.
     */
    private String headerEncoded;

    /**
     * Base64-encoded HTML footer (optional).
     * May contain embedded data URI images in img src attributes.
     */
    private String footerEncoded;

    /**
     * Dynamic data map for Handlebars templating (optional).
     * Contains server-side trusted data.
     */
    private Map<String,Object> docPropertiesJsonData;
}
