# Java Hybrid Document Generator

## Table of Contents
- [Project Overview](#project-overview)
- [Quick Start](#quick-start)
- [Architecture Pipeline](#architecture-pipeline)
- [API Reference](#api-reference)
- [Image Handling](#image-handling)
- [Technical Details](#technical-details--constraints)
- [Error Handling & Logging](#error-handling--logging)
- [Common Issues & Solutions](#common-issues--solutions)
- [Recent Updates](#recent-updates)

## Project Overview
This application is a specialized microservice that generates high-fidelity PDFs using a **template-first hybrid** approach. It combines strict HTML/CSS layout control with **Markdown** for dynamic, user-friendly content formatting.

### Core Philosophy
- **Layout is HTML:** Headers, footers, columns, and page breaks are handled by Handlebars-driven HTML templates.
- **Content is Markdown:** Dynamic text, lists, and tables stay as Markdown for clean data loops without brittle string concatenation.

## Quick Start

### API Endpoint
```
POST /api/content/submit
Content-Type: application/json
```

### Request Example
```json
{
  "templateEncoded": "base64EncodedTemplate",
  "cssEncoded": "base64EncodedCSS",
  "headerEncoded": "base64EncodedHeader",
  "footerEncoded": "base64EncodedFooter",
  "imageEncoded": "base64EncodedImage",  // Optional
  "docPropertiesJsonData": {
    "title": "My Document",
    "items": [
      {"name": "Item 1", "value": 100},
      {"name": "Item 2", "value": 200}
    ]
  }
}
```

### Response
- **Success (200 OK):**
  - **Content-Type:** `application/pdf`
  - **Body:** PDF binary stream
  - **Disposition:** `inline; filename=generated_report.pdf`

- **Error Responses:**
  - **400 Bad Request:** Invalid input (null request, missing required fields, invalid Base64 encoding)
    - Error message provides specific details about the validation failure
  - **500 Internal Server Error:** Processing errors (template compilation failures, PDF generation errors)
    - Error messages include context for debugging

### Minimal Example
```java
GenerateRequestDto request = new GenerateRequestDto();
request.setTemplateEncoded(Base64.getEncoder().encodeToString(templateHtml.getBytes()));
request.setCssEncoded(Base64.getEncoder().encodeToString(css.getBytes()));
request.setDocPropertiesJsonData(Map.of("title", "Hello World"));

// POST to /api/content/submit
// Returns PDF binary
```

## Architecture Pipeline

### Visual Flowchart

```mermaid
flowchart TD
    Start([API Request<br/>POST /api/content/submit]) --> Validate[Stage 1: Input Validation]
    
    Validate --> ValidateRequest{Request<br/>valid?}
    ValidateRequest -->|Invalid| Error1[Return 400<br/>IllegalArgumentException]
    ValidateRequest -->|Valid| ValidateTemplate{templateEncoded<br/>provided?}
    ValidateTemplate -->|Missing| Error2[Return 400<br/>Template Required]
    ValidateTemplate -->|Valid| Decode[Stage 2: Input Decoding]
    
    Decode --> DecodeTemplate[Decode templateEncoded<br/>Base64 → HTML]
    Decode --> DecodeCSS[Decode cssEncoded<br/>Base64 → CSS]
    Decode --> DecodeHeader[Decode headerEncoded<br/>Base64 → HTML]
    Decode --> DecodeFooter[Decode footerEncoded<br/>Base64 → HTML]
    Decode --> DecodeImage[Decode imageEncoded<br/>Base64 → Image Data]
    Decode --> GetData[Extract docPropertiesJsonData<br/>Trusted Server-Side Data]
    
    DecodeTemplate --> ImageCheck{imageEncoded<br/>provided?}
    DecodeCSS --> ImageCheck
    DecodeHeader --> ImageCheck
    DecodeFooter --> ImageCheck
    DecodeImage --> ImageCheck
    GetData --> ImageCheck
    
    ImageCheck -->|Yes| ImagePreProcess[Stage 3: Image Pre-Processing]
    ImageCheck -->|No| Handlebars[Stage 4: Templating Handlebars]
    
    ImagePreProcess --> ValidateImage[Validate Image Type<br/>PNG/JPG/SVG only]
    ValidateImage -->|Valid| ProcessHeader[Process Header HTML<br/>Replace img src with data URI]
    ValidateImage -->|Invalid| FallbackStatic[Use Static Images<br/>from resources/static/]
    ProcessHeader --> ProcessFooter[Process Footer HTML<br/>Replace img src with data URI]
    ProcessFooter --> Handlebars
    FallbackStatic --> Handlebars
    
    Handlebars --> MergeData[Merge docPropertiesJsonData<br/>into Handlebars Template]
    MergeData --> ExpandLoops[Expand Loops & Conditionals<br/>{{#each}}, {{#if}}, etc.]
    ExpandLoops --> HybridOutput[Output: Hybrid HTML/Markdown<br/>Template structure + Raw Markdown]
    
    HybridOutput --> DOMParse[Stage 5: DOM Processing<br/>Parse with Jsoup]
    DOMParse --> FindMDTags[Locate &lt;md&gt; tags<br/>in Document]
    FindMDTags --> RenderMarkdown[Render Markdown to HTML<br/>via Flexmark Parser]
    RenderMarkdown --> ReplaceInPlace[Replace &lt;md&gt; nodes<br/>with rendered HTML in-place]
    ReplaceInPlace --> PureHTML[Output: Pure HTML DOM<br/>All Markdown converted]
    
    PureHTML --> Assembly[Stage 6: Assembly & Image Processing]
    Assembly --> InjectCSS[Inject CSS into &lt;head&gt;<br/>from cssStr]
    InjectCSS --> InjectFooter[Inject Footer into &lt;body&gt;<br/>prepend footerStr]
    InjectFooter --> InjectHeader[Inject Header into &lt;body&gt;<br/>prepend headerStr]
    
    InjectHeader --> ImageCheck2{imageEncoded<br/>provided?}
    ImageCheck2 -->|Yes| ProcessAllImages[Process All &lt;img&gt; Tags<br/>Replace src with data URIs]
    ImageCheck2 -->|No| EnforceXHTML[Enforce XHTML Compliance<br/>Auto-close tags, escape entities]
    ProcessAllImages --> EnforceXHTML
    
    EnforceXHTML --> ValidXHTML[Output: Valid XHTML DOM<br/>Ready for PDF Rendering]
    
    ValidXHTML --> PDFRender[Stage 7: Rendering iText7]
    PDFRender --> ConfigureProps[Configure ConverterProperties<br/>Set Base URI for static assets]
    ConfigureProps --> ConvertToPDF[HtmlConverter.convertToPdf<br/>XHTML → PDF Binary]
    ConvertToPDF --> PDFOutput[Output: PDF Binary Stream<br/>Content-Type: application/pdf]
    
    PDFOutput --> End([Response<br/>PDF Document])
    
    style Start fill:#e1f5ff
    style End fill:#c8e6c9
    style Validate fill:#ffcdd2
    style ValidateRequest fill:#ffcdd2
    style ValidateTemplate fill:#ffcdd2
    style Error1 fill:#ffccbc
    style Error2 fill:#ffccbc
    style ImageCheck fill:#fff9c4
    style ImageCheck2 fill:#fff9c4
    style ValidateImage fill:#fff9c4
    style FallbackStatic fill:#ffccbc
    style Handlebars fill:#e1bee7
    style DOMParse fill:#b3e5fc
    style Assembly fill:#c5cae9
    style PDFRender fill:#f8bbd0
```

> **Note:** For a static image version, see [flexmark_flowchart.png](./flowchart/flexmark_flowchart.png)

### Pipeline Stages

The service follows a strict **7-stage pipeline** to ensure formatting compliance and robust error handling:

| Stage | Process | Output |
|-------|---------|--------|
| **1. Input Validation** | Validate request object is not null; ensure `templateEncoded` is provided and not empty | Validated request or error response |
| **2. Input Decoding** | Decode Base64-encoded inputs (template, CSS, header, footer, image); handle decoding errors gracefully | Raw HTML/CSS strings |
| **3. Image Pre-Processing** | Process header/footer HTML to replace `<img>` src with data URIs (if dynamic image provided) | Processed HTML strings |
| **4. Templating (Handlebars)** | Merge data into HTML structure; loops expand while content remains raw Markdown | Hybrid HTML/Markdown string |
| **5. DOM Processing** | Parse hybrid string → locate `<md>` tags → render Markdown → replace in-place | Pure HTML DOM |
| **6. Assembly & Image Processing** | Inject CSS/headers/footers; update all `<img>` tags with data URIs (if needed) | Valid XHTML DOM |
| **7. Rendering (iText7)** | Convert XHTML to PDF binary | PDF stream |

> **Note:** Input sanitization is not performed as `docPropertiesJsonData` originates from server-side sources and is considered trusted. This design decision improves performance and preserves formatting flexibility.

### Key Pipeline Details

**Stage 1 - Input Validation:**
- Validates that the request object is not null
- Ensures `templateEncoded` is provided and not empty
- Returns descriptive error messages for validation failures
- Prevents processing invalid requests early in the pipeline

**Stage 4 - Templating:**
- Data from `docPropertiesJsonData` is merged into the Handlebars template
- Loops and conditionals expand while embedded Markdown content remains raw
- Result is a hybrid HTML/Markdown string
- Template compilation errors are caught and logged with context

**Stage 5 - DOM Processing:**
- Parse the hybrid string into a Jsoup `Document`
- Locate custom `<md>` tags
- Render the enclosed Markdown to HTML via Flexmark
- Optimized processing: filters empty lines and uses efficient string joining
- Replace each `<md>` node in place with the rendered HTML nodes

**Stage 6 - Assembly:**
- CSS, headers, and footers are injected directly into the DOM
- If a dynamic image was provided, all remaining `<img>` tags are updated with data URIs
- Image processing uses shared validation logic for consistency
- XHTML syntax is enforced automatically
- Processing errors are logged with warnings, allowing graceful fallback to static images

## API Reference

### GenerateRequestDto

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `templateEncoded` | String | **Yes** | Base64-encoded HTML template with Handlebars syntax. Must not be null or empty. |
| `cssEncoded` | String | No | Base64-encoded CSS styles |
| `headerEncoded` | String | No | Base64-encoded HTML header |
| `footerEncoded` | String | No | Base64-encoded HTML footer |
| `imageEncoded` | String | No | Base64-encoded image (PNG, JPG, or SVG). Supports data URIs, Base64-encoded strings, or raw Base64 data. |
| `docPropertiesJsonData` | Map<String, Object> | No* | Dynamic data for Handlebars templating (server-side, trusted source). Defaults to empty map if null. |

> **Note:** The service performs validation on incoming requests. Invalid requests (null request object or missing `templateEncoded`) will return an `IllegalArgumentException` with a descriptive error message.

### Template Syntax

**Handlebars Variables:**
```handlebars
<h1>{{title}}</h1>
<p>Generated on {{date}}</p>
```

**Handlebars Loops:**
```handlebars
{{#each items}}
  <div class="item">
    <md>
    ## {{name}}
    Value: {{value}}
    </md>
  </div>
{{/each}}
```

**Markdown Blocks:**
Wrap dynamic Markdown content in `<md>` tags:
```html
<md>
## Section Title
- Item 1
- Item 2

| Column 1 | Column 2 |
|----------|----------|
| Data 1   | Data 2   |
</md>
```

## Image Handling

### Overview
The service supports both **static** and **dynamic** images. Dynamic images take precedence when provided, with automatic fallback to static assets.

### Static Images
- **Location:** Place assets in `src/main/resources/static/`
- **Usage:** Reference with relative paths: `<img src="my-logo.png">`
- **Fallback:** Used when no dynamic image is provided or validation fails
- **Works:** Both locally and in packaged JARs

### Dynamic Images (v4.0)

#### Supported Formats
- ✅ PNG (`.png`)
- ✅ JPEG (`.jpg`, `.jpeg`)
- ✅ SVG (`.svg`)

#### Processing Flow

**1. Decoding & Validation**
```
Base64 Input → Decode → Detect MIME Type → Validate Format
```

The system handles multiple input formats:
- Data URIs: `data:image/png;base64,iVBORw0KGgo...` (used as-is)
- Base64-encoded strings: Decoded to extract raw Base64 data
- Raw Base64: Used directly

**2. MIME Type Detection**
- **PNG:** Magic bytes `89 50 4E 47` (hex)
- **JPEG:** Magic bytes `FF D8 FF` (hex)
- **SVG:** XML markers (`<?xml`, `<svg`, or SVG namespace)

**3. Image Replacement**
- **Pre-Injection (Stage 3):** Header and footer HTML strings are processed
- **Post-Assembly (Stage 6):** Final DOM is processed to catch all images
- All `<img>` tags receive: `data:{mimeType};base64,{base64Data}`
- Processing errors are logged and gracefully fall back to static images

#### Fallback Behavior
| Scenario | Result |
|----------|--------|
| `imageEncoded` is `null` or empty | Uses static images from `resources/static/` |
| Unsupported image type | Processing skipped, uses static images |
| Processing error | Original HTML preserved, uses static images |

#### Example
```java
// Encode image to Base64
String imageBase64 = Base64.getEncoder()
    .encodeToString(Files.readAllBytes(Paths.get("logo.png")));

GenerateRequestDto request = new GenerateRequestDto();
request.setImageEncoded(imageBase64);
// All <img> tags in template, header, and footer will use this image
```

## Recent Updates

### v5.0: Code Quality & Performance Improvements
- **Input Validation:** Enhanced validation with explicit null checks and required field validation. The service now validates that:
  - Request object is not null
  - `templateEncoded` field is required and cannot be empty
  - Provides clear error messages for invalid inputs
- **Error Handling:** Improved exception handling with:
  - More descriptive error messages that include context
  - Proper exception chaining for debugging
  - Graceful fallback behavior with logging
- **Logging:** Added comprehensive SLF4J logging throughout the pipeline:
  - Debug logs for pipeline stages and processing details
  - Warning logs for fallback scenarios and non-critical failures
  - Error logs with full exception context for troubleshooting
- **Performance Optimizations:**
  - Optimized markdown processing using `Collectors.joining()` instead of reduce operations
  - Improved string handling with empty line filtering
  - More efficient image processing with shared validation logic
- **Code Quality:**
  - Eliminated code duplication between image processing methods
  - Extracted reusable methods for better maintainability
  - Added constants for magic strings and MIME types
  - Improved method organization and separation of concerns
- **Benefit:** More robust error handling, better debugging capabilities, improved performance, and easier maintenance.

### v4.0: Dynamic Image Processing
- **Feature:** Support for Base64-encoded images via `GenerateRequestDto.imageEncoded` field.
- **Capabilities:**
  - Class-agnostic image processing that works with templates, headers, and footers.
  - Automatic image type validation (only `.png`, `.jpg`/`.jpeg`, and `.svg` are supported).
  - Intelligent fallback to static images from `resources/static/` when no encoded image is provided or validation fails.
- **Processing Flow:** Images are processed at two stages:
  1. **Pre-injection:** Header and footer HTML strings are processed before DOM assembly.
  2. **Post-assembly:** Final DOM is processed to catch template images and dynamically generated content.
- **Benefit:** Enables dynamic branding and customization per document generation request while maintaining backward compatibility with static assets.

### v3.0: PDF Engine Migration (iText7)
- **Previous Engine:** `xhtmlrenderer` (Flying Saucer).
- **Current Engine:** `iText7 html2pdf`.
- **Benefit:** Migrated to a modern, actively maintained library with significantly improved CSS support (including Flexbox and Grid), better font handling, and enhanced performance.

### v2.0: Performance Refactor (Regex vs. DOM)
- **Previous:** Regex to find `<md>` blocks, then separate render + string concatenation to rebuild HTML (fragile and slow on large inputs).
- **Current:** Single-pass parse into a Jsoup object model; traverse to replace `<md>` elements in place.
- **Benefit:** Faster execution, lower memory overhead (no duplicate string buffers), and higher robustness against malformed tags.

## Technical Details & Constraints

### CSS Compliance
- **Engine:** `iText7 html2pdf`
- **Support:** Excellent support for modern CSS standards, including **Flexbox and Grid layouts**
- **Benefit:** Removes previous CSS 2.1 limitations, enabling complex and responsive PDF designs

### HTML Requirements
- **Strict XHTML:** All HTML tags must be well-formed and closed (e.g., `<br />`, not `<br>`)
- **Auto-Enforcement:** The Jsoup assembly step automatically enforces XHTML compliance before rendering
- **Validation:** Malformed HTML is corrected during the assembly stage

### Markdown Support
- **Extensions:** Tables, Attributes
- **Code Blocks:** Use triple backticks (```) instead of indented blocks
- **HTML in Markdown:** HTML tags in templates pass through (configured for template-first approach)

### Security Considerations
- **Input Sanitization:** Not performed on `docPropertiesJsonData` as it originates from trusted server-side sources
- **Template/CSS/Header/Footer:** These are Base64-encoded and decoded, but not sanitized as they are considered trusted server-side resources
- **Input Validation:** The service validates request structure and required fields to prevent processing invalid requests
- **Error Messages:** Error messages are designed to provide debugging information without exposing sensitive system details
- **Design Rationale:** Skipping sanitization improves performance and preserves formatting flexibility while maintaining security through controlled data sources and proper validation

### Error Handling & Logging

The service includes comprehensive error handling and logging:

- **Validation Errors:** Invalid requests return `IllegalArgumentException` with descriptive messages
- **Processing Errors:** Template compilation or PDF generation failures return `RuntimeException` with context
- **Logging:** All errors are logged with appropriate levels:
  - **DEBUG:** Pipeline stages, processing details, image counts
  - **WARN:** Fallback scenarios, non-critical processing failures
  - **ERROR:** Exceptions with full stack traces and context

**Logging Configuration:**
To enable debug logging for troubleshooting, configure your logging framework (e.g., Logback, Log4j2):
```properties
# application.properties or logback.xml
logging.level.com.flexmark.flexMarkProject.service.MarkdownService=DEBUG
```

### Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| `IllegalArgumentException: Request cannot be null` | Request object is null | Ensure request object is properly constructed |
| `IllegalArgumentException: Template is required` | `templateEncoded` is missing or empty | Provide a valid Base64-encoded template |
| `IllegalArgumentException: Invalid Base64 encoding` | Base64 string is malformed | Verify Base64 encoding is correct |
| Images not appearing | Invalid Base64 or unsupported format | Verify Base64 encoding, ensure PNG/JPG/SVG. Check logs for validation failures. |
| PDF generation fails | Malformed HTML | Ensure all tags are closed (`<br />` not `<br>`). Check error logs for details. |
| Markdown not rendering | Missing `<md>` tags | Wrap Markdown content in `<md>` tags |
| Template variables not replaced | Invalid Handlebars syntax | Check `{{variable}}` syntax and data map keys |
| CSS not applying | CSS not Base64 encoded | Ensure CSS is properly Base64 encoded in request |
| Processing silently fails | Check application logs | Enable DEBUG logging to see detailed pipeline execution |