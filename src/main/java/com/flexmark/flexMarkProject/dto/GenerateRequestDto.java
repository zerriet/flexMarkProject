package com.flexmark.flexMarkProject.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateRequestDto implements Serializable{
    private String filePath;
    private String templateEncoded;
    private String templateName;
    private String cssEncoded;
    // CHANGED: Split into two separate fields
    private String headerEncoded;
    private String footerEncoded;
    private String imageEncoded;
    private Map<String,Object> docPropertiesJsonData;
}
