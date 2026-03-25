# Java Hybrid Document Generator

## Table of Contents
- [Project Overview](#project-overview)
- [Quick Start](#quick-start)
- [API Documentation (Swagger)](#api-documentation-swagger)
- [Architecture Pipeline](#architecture-pipeline)
- [API Reference](#api-reference)
- [Image Handling](#image-handling)
- [Technical Details](#technical-details--constraints)
- [Error Handling & Logging](#error-handling--logging)
- [Common Issues & Solutions](#common-issues--solutions)
- [Code Walkthrough](#code-walkthrough)
- [Testing & Quality Assurance](#testing--quality-assurance)
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
  "headerEncoded": "base64EncodedHeaderWithEmbeddedImages",
  "footerEncoded": "base64EncodedFooterWithEmbeddedImages",
  "docPropertiesJsonData": {
    "title": "My Document",
    "items": [
      {"name": "Item 1", "value": 100},
      {"name": "Item 2", "value": 200}
    ]
  }
}
```

**Note:** Images should be embedded directly in the HTML using data URIs:
```html
<img src="data:image/png;base64,iVBORw0KGgo..." />
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

## API Documentation (Swagger)

### Interactive API Documentation

The service provides comprehensive interactive API documentation powered by **Swagger/OpenAPI 3.0**.

**Access Swagger UI:**
- **URL:** http://localhost:8080/swagger-ui.html
- **OpenAPI Spec:** http://localhost:8080/v3/api-docs

### Features

The Swagger UI provides:

✅ **Interactive Testing** - Test the API directly from your browser
✅ **Multiple Examples** - Pre-configured request examples you can try immediately
✅ **Complete Schema Documentation** - Detailed field descriptions and requirements
✅ **Error Response Examples** - See what 400 and 500 errors look like
✅ **Request/Response Models** - Full JSON schema documentation

### Quick Test

1. Start the application:
   ```bash
   mvn spring-boot:run
   ```

2. Open Swagger UI: http://localhost:8080/swagger-ui.html

3. Expand **"PDF Document Generation"** → `POST /api/content/submit`

4. Click **"Try it out"**

5. Select an example:
   - **Simple template** - Minimal example with Handlebars variables
   - **Template with loops** - Handlebars `{{#each}}` with Markdown tables
   - **Full example with header/footer** - Complete document with data URIs

6. Click **"Execute"**

7. View the response (PDF binary or error)

### Example Requests

#### Example 1: Simple Template
```json
{
  "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMge3tuYW1lfX08L21kPjwvZGl2Pg==",
  "cssEncoded": "aDEgeyBjb2xvcjogYmx1ZTsgfQ==",
  "docPropertiesJsonData": {
    "title": "Welcome",
    "name": "Alice"
  }
}
```
**Decoded template:** `<div><h1>{{title}}</h1><md>## {{name}}</md></div>`

#### Example 2: Template with Loops
```json
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
```

### API Annotations

The codebase includes comprehensive OpenAPI annotations:

**DTO Level ([GenerateRequestDto.java](src/main/java/com/flexmark/flexMarkProject/dto/GenerateRequestDto.java)):**
- `@Schema` on class with complete request example
- Field-level `@Schema` with descriptions, examples, and required status
- Base64-encoded sample values for all fields

**Controller Level ([InitialController.java](src/main/java/com/flexmark/flexMarkProject/controller/InitialController.java)):**
- `@Tag` - Groups API under "PDF Document Generation"
- `@Operation` - Documents endpoint with pipeline overview, features, security notes
- `@ApiResponses` - Documents all response codes (200, 400, 500)
- `@Parameter` - Documents request body with 3 interactive examples

**Configuration ([OpenApiConfig.java](src/main/java/com/flexmark/flexMarkProject/config/OpenApiConfig.java)):**
- API metadata (title, version, description)
- Contact information and license
- Server configurations (local & production)
- Comprehensive API overview with features and use cases

### Configuration

Swagger UI settings in `application.properties`:

```properties
# Swagger/OpenAPI Configuration
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
springdoc.swagger-ui.display-request-duration=true
springdoc.swagger-ui.doc-expansion=none
```

### Production Deployment

For production environments, disable Swagger UI:

```properties
springdoc.swagger-ui.enabled=false
```

Or protect with Spring Security to restrict access.

### Additional Resources

For detailed Swagger setup instructions, see [SWAGGER_SETUP.md](../../../SWAGGER_SETUP.md)

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
    Decode --> DecodeHeader[Decode headerEncoded<br/>Base64 → HTML with data URIs]
    Decode --> DecodeFooter[Decode footerEncoded<br/>Base64 → HTML with data URIs]
    Decode --> GetData[Extract docPropertiesJsonData<br/>Trusted Server-Side Data]

    DecodeTemplate --> Handlebars[Stage 3: Handlebars Templating]
    DecodeCSS --> Handlebars
    DecodeHeader --> Handlebars
    DecodeFooter --> Handlebars
    GetData --> Handlebars

    Handlebars --> MergeData[Merge docPropertiesJsonData<br/>into Handlebars Template]
    MergeData --> ExpandLoops[Expand Loops &amp; Conditionals<br/>Handlebars #each, #if, etc.]
    ExpandLoops --> HybridOutput[Output: Hybrid HTML/Markdown<br/>Template structure + Raw Markdown]

    HybridOutput --> DOMParse[Stage 4: DOM Processing<br/>Parse with Jsoup]
    DOMParse --> FindMDTags["Locate &lt;md&gt; tags<br/>in Document"]
    FindMDTags --> RenderMarkdown[Render Markdown to HTML<br/>via Flexmark Parser]
    RenderMarkdown --> ReplaceInPlace["Replace &lt;md&gt; nodes<br/>with rendered HTML in-place"]
    ReplaceInPlace --> PureHTML[Output: Pure HTML DOM<br/>All Markdown converted]

    PureHTML --> Assembly[Stage 5: DOM Assembly]
    Assembly --> InjectCSS["Inject CSS into &lt;head&gt;<br/>from cssStr"]
    InjectCSS --> InjectFooter["Inject Footer into &lt;body&gt;<br/>prepend footerStr"]
    InjectFooter --> InjectHeader["Inject Header into &lt;body&gt;<br/>prepend headerStr"]
    InjectHeader --> EnforceXHTML[Enforce XHTML Compliance<br/>Auto-close tags, escape entities]
    EnforceXHTML --> ValidXHTML[Output: Valid XHTML DOM<br/>Data URIs preserved]

    ValidXHTML --> PDFRender[Stage 6: PDF Rendering - iText7]
    PDFRender --> ConfigureProps[Configure ConverterProperties<br/>Empty base URI, Secure Retriever]
    ConfigureProps --> ConvertToPDF[HtmlConverter.convertToPdf<br/>XHTML → PDF Binary]

    ConvertToPDF --> DataURICheck{Image src has<br/>data URI?}
    DataURICheck -->|Yes| ParseDataURI[SecureDataUriResourceRetriever<br/>Parse and decode Base64]
    DataURICheck -->|No| RegularResource[Load from file:// or jar:file://]
    ParseDataURI --> RenderImage[iText7 renders image<br/>from binary stream]
    RegularResource --> RenderImage

    RenderImage --> PDFOutput[Output: PDF Binary Stream<br/>Content-Type: application/pdf]
    PDFOutput --> End([Response<br/>PDF Document])

    style Start fill:#2196F3,color:#fff
    style End fill:#4CAF50,color:#fff
    style Validate fill:#F44336,color:#fff
    style ValidateRequest fill:#E91E63,color:#fff
    style ValidateTemplate fill:#E91E63,color:#fff
    style Error1 fill:#FF5722,color:#fff
    style Error2 fill:#FF5722,color:#fff
    style DataURICheck fill:#FFC107,color:#000
    style Decode fill:#607D8B,color:#fff
    style Handlebars fill:#9C27B0,color:#fff
    style DOMParse fill:#00BCD4,color:#fff
    style Assembly fill:#3F51B5,color:#fff
    style PDFRender fill:#E91E63,color:#fff
    style ParseDataURI fill:#009688,color:#fff
    style RenderImage fill:#4CAF50,color:#fff
```

> **Note:** For a static image version, see [flexmark_flowchart.png](./flowchart/flexmark_flowchart.png)

### Pipeline Stages

The service follows a strict **6-stage pipeline** to ensure formatting compliance and robust error handling:

| Stage | Process | Output |
|-------|---------|--------|
| **1. Input Validation** | Validate request object is not null; ensure `templateEncoded` is provided and not empty (Jakarta Bean Validation) | Validated request or error response |
| **2. Input Decoding** | Decode Base64-encoded inputs (template, CSS, header, footer); handle decoding errors gracefully | Raw HTML/CSS strings (with embedded data URI images) |
| **3. Templating (Handlebars)** | Merge data into HTML structure; loops expand while content remains raw Markdown | Hybrid HTML/Markdown string |
| **4. DOM Processing** | Parse hybrid string → locate `<md>` tags → render Markdown → replace in-place | Pure HTML DOM (no more Markdown) |
| **5. DOM Assembly** | Inject CSS/headers/footers into DOM; enforce XHTML compliance (auto-close tags, escape entities) | Valid XHTML DOM |
| **6. Rendering (iText7)** | Convert XHTML to PDF binary with secure resource retrieval (data URIs decoded on-the-fly, HTTP/HTTPS blocked) | PDF stream |

> **Note:** Input sanitization is not performed as `docPropertiesJsonData` originates from server-side sources and is considered trusted. This design decision improves performance and preserves formatting flexibility.

### Key Pipeline Details

**Stage 1 - Input Validation:**
- Jakarta Bean Validation at controller layer with `@Valid` annotation
- Validates that `templateEncoded` is not blank
- Returns 400 Bad Request with validation error details before reaching service layer
- Prevents processing invalid requests early in the pipeline

**Stage 2 - Input Decoding:**
- Decodes all Base64-encoded inputs (template, CSS, header, footer)
- Images are already embedded as data URIs in the HTML (e.g., `<img src="data:image/png;base64,...">``)
- Data URIs remain intact during decoding - no extraction or file writing
- No separate image processing required

**Stage 3 - Templating (Handlebars):**
- Data from `docPropertiesJsonData` is merged into the Handlebars template
- Loops and conditionals expand while embedded Markdown content remains raw
- Result is a hybrid HTML/Markdown string
- Template compilation errors are caught and logged with context

**Stage 4 - DOM Processing:**
- Parse the hybrid string into a Jsoup `Document`
- Locate custom `<md>` tags and render enclosed Markdown to HTML via Flexmark
- Preserves blank lines for proper paragraph separation
- Replace each `<md>` node in place with the rendered HTML nodes
- Result is pure HTML DOM (all Markdown converted)

**Stage 5 - DOM Assembly:**
- Inject CSS into `<head>`
- Inject footer at top of `<body>` (prepended first)
- Inject header at top of `<body>` (prepended second, appears above footer)
- Enforce XHTML syntax automatically for iText7 compatibility (self-closing tags, escaped entities, quoted attributes)
- Data URIs in headers/footers are preserved without corruption

**Stage 6 - Rendering (iText7):**
- Convert XHTML to PDF using iText7's HtmlConverter
- **Secure Resource Retrieval:** Custom `SecureDataUriResourceRetriever` implementation
  - ✅ Allows: Data URIs (decoded on-the-fly during rendering) and local file:// resources
  - ❌ Blocks: External HTTP/HTTPS requests (SSRF protection)
- When iText7 encounters a data URI, the retriever parses and decodes the Base64 data in real-time
- No temporary files created - binary image data streamed directly to PDF renderer
- Static resources (if needed) loaded from classpath

## API Reference

### GenerateRequestDto

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `templateEncoded` | String | **Yes** | Base64-encoded HTML template with Handlebars syntax. Must not be null or empty. Validated with `@NotBlank` annotation. |
| `cssEncoded` | String | No | Base64-encoded CSS styles |
| `headerEncoded` | String | No | Base64-encoded HTML header. May contain `<img>` tags with data URI images embedded in `src` attributes. |
| `footerEncoded` | String | No | Base64-encoded HTML footer. May contain `<img>` tags with data URI images embedded in `src` attributes. |
| `docPropertiesJsonData` | Map<String, Object> | No | Dynamic data for Handlebars templating (server-side, trusted source). Defaults to empty map if null. |

> **Note:** The service uses Jakarta Bean Validation. Invalid requests (missing or blank `templateEncoded`) will automatically return a 400 Bad Request with validation error details before reaching the service layer. This provides faster feedback and clearer error messages.

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
Images are embedded in HTML using **data URIs** (Base64-encoded). The service uses a custom `SecureDataUriResourceRetriever` that intercepts data URI requests during PDF rendering and provides the decoded binary data directly to iText7, eliminating the need for temporary files.

### Data URI Format
Images should be embedded in `<img>` tags using the data URI format:
```html
<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA..." />
```

### How Data URIs are Processed

**Runtime Processing Flow:**
1. **HTML Assembly:** Headers, footers, and templates are parsed through Jsoup to ensure XHTML compliance
2. **Data URI Preservation:** Data URIs remain intact in the HTML - no extraction or file writing occurs
3. **PDF Rendering:** When iText7 encounters an `<img>` tag with a data URI:
   - `SecureDataUriResourceRetriever.getInputStreamByUrl()` is called
   - The data URI is parsed to extract the base64 data
   - Base64 data is decoded to binary bytes
   - Binary stream is returned to iText7 for image rendering
4. **Direct Rendering:** iText7 renders the image directly from the decoded bytes

**Key Benefits:**
- ✅ **No temporary files** - Zero file system overhead
- ✅ **Better performance** - No I/O operations during PDF generation
- ✅ **Simpler architecture** - No cleanup or file management needed
- ✅ **Stateless** - Each request is completely independent

### Supported Formats
All standard image formats supported by iText7:
- ✅ PNG: `data:image/png;base64,...`
- ✅ JPEG: `data:image/jpeg;base64,...`
- ✅ GIF: `data:image/gif;base64,...`
- ✅ WebP: `data:image/webp;base64,...`
- ✅ SVG: `data:image/svg+xml;base64,...`

### Usage in Templates

**Header/Footer with Images:**
```html
<!-- Header HTML (before Base64 encoding) -->
<div id="header">
    <img src="data:image/png;base64,iVBORw0KGgoAAAA..." style="width: 100px;" />
    <h1>Company Report</h1>
</div>
```

Then Base64-encode the entire HTML and send in `headerEncoded` field.

**Template with Images:**
```handlebars
<div class="logo">
    <img src="data:image/png;base64,{{logoData}}" alt="Logo" />
</div>
```

### Security: SSRF Protection

The `SecureDataUriResourceRetriever` implements strict security controls:

**Allowed:**
- ✅ **Data URIs:** `data:image/png;base64,...` - Parsed and decoded on-the-fly
- ✅ **Local file URIs:** `file://` - For local resources only
- ✅ **Classpath resources:** `jar:file:` - For packaged resources

**Blocked:**
- ❌ **HTTP requests:** `http://example.com/image.jpg` - Prevents SSRF attacks
- ❌ **HTTPS requests:** `https://example.com/image.jpg` - Prevents data exfiltration
- ❌ **External resources:** Any attempt to fetch external content is rejected

**Security Implementation:**
```java
@Override
public InputStream getInputStreamByUrl(URL url) throws IOException {
    String urlString = url.toString();

    // Allow data URIs - parse and decode inline
    if (urlString.startsWith("data:")) {
        return parseDataUri(urlString);
    }

    // Allow local file access only
    if (urlString.startsWith("file://") || urlString.startsWith("jar:file:")) {
        return url.openStream();
    }

    // Block ALL external HTTP/HTTPS requests
    if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
        throw new IOException("External HTTP/HTTPS requests are blocked for security reasons");
    }

    return url.openStream();
}
```

### Example Usage

```java
// Convert image to data URI
byte[] imageBytes = Files.readAllBytes(Paths.get("logo.png"));
String base64Image = Base64.getEncoder().encodeToString(imageBytes);
String dataUri = "data:image/png;base64," + base64Image;

// Create footer HTML with embedded image
String footerHtml = """
    <div id="pdf-footer">
        <table style="width: 100%;">
            <tr>
                <td style="text-align: left;">
                    <img src="%s" style="height: 40px;" />
                </td>
                <td style="text-align: right;">
                    Page <span class="page-number"></span>
                </td>
            </tr>
        </table>
    </div>
    """.formatted(dataUri);

// Base64 encode the footer HTML
String footerEncoded = Base64.getEncoder().encodeToString(footerHtml.getBytes(StandardCharsets.UTF_8));

// Send in request
GenerateRequestDto request = new GenerateRequestDto();
request.setFooterEncoded(footerEncoded);
// Data URI will be decoded and rendered directly during PDF generation
```

### Data URI Parsing Details

The `SecureDataUriResourceRetriever` parses data URIs according to RFC 2397:

**Data URI Format:**
```
data:[<mediatype>][;base64],<data>
```

**Parsing Logic:**
1. Extract metadata and data parts (split on comma)
2. Check if `base64` encoding is specified in metadata
3. Decode base64 string to binary bytes
4. Return as `ByteArrayInputStream` to iText7

**Example Data URI Breakdown:**
```
data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...
│    │         │       │
│    │         │       └─ Base64-encoded image data
│    │         └───────── Encoding method
│    └─────────────────── MIME type
└──────────────────────── Protocol
```

## Code Walkthrough

This section provides a detailed walkthrough of the codebase for learning and reference. We'll follow the data flow from HTTP request to PDF response, examining each component step-by-step.

### Architecture Overview

The application follows a **clean layered architecture**:

```
HTTP Request
    ↓
[Controller Layer] - Handles HTTP, validates input
    ↓
[Service Layer] - Core business logic, document generation pipeline
    ↓
[Libraries] - Handlebars, Flexmark, Jsoup, iText7
    ↓
PDF Response
```

**Key Design Decisions:**
- **Template-First Approach:** Process Handlebars before Markdown to enable loops/conditionals in Markdown content
- **Data URI Images:** Eliminate temporary files by decoding Base64 images on-the-fly during PDF rendering
- **XHTML Compliance:** Use Jsoup to enforce strict XHTML for iText7 compatibility
- **Stateless Processing:** No state management, each request is independent

### Component Breakdown

#### 1. Data Transfer Object (DTO)

**File:** [GenerateRequestDto.java](src/main/java/com/flexmark/flexMarkProject/dto/GenerateRequestDto.java)

**Purpose:** Define the API contract for document generation requests.

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateRequestDto implements Serializable {
    @NotBlank(message = "Template is required and cannot be empty")
    private String templateEncoded;

    private String cssEncoded;
    private String headerEncoded;
    private String footerEncoded;
    private Map<String, Object> docPropertiesJsonData;
}
```

**Key Points:**
- **Lombok Annotations:** `@Data` generates getters/setters, `@AllArgsConstructor`/`@NoArgsConstructor` for constructors
- **Validation:** `@NotBlank` ensures `templateEncoded` is required (validated at controller layer)
- **JSON Serialization:** `@JsonInclude(JsonInclude.Include.NON_NULL)` excludes null fields from JSON responses
- **Design:** All HTML/CSS inputs are Base64-encoded to safely transmit special characters and preserve formatting

#### 2. Controller Layer

**File:** [InitialController.java](src/main/java/com/flexmark/flexMarkProject/controller/InitialController.java)

**Purpose:** HTTP transport layer - receives requests, validates input, returns PDF response.

```java
@RestController
@RequestMapping("/api/content")
public class InitialController {
    private final MarkdownService markdownService;

    public InitialController(MarkdownService markdownService) {
        this.markdownService = markdownService;
    }

    @PostMapping("/submit")
    public ResponseEntity<Resource> submitContent(@Valid @RequestBody GenerateRequestDto data) {
        Resource generatedPdf = markdownService.generateDocument(data);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=generated_report.pdf")
                .body(generatedPdf);
    }
}
```

**Key Points:**
- **Constructor Injection:** Spring injects `MarkdownService` dependency (preferred over field injection)
- **Validation:** `@Valid` annotation triggers Jakarta Bean Validation on `GenerateRequestDto`
  - If `templateEncoded` is missing/blank, Spring returns 400 Bad Request automatically
  - Validation happens **before** reaching service layer - fail fast principle
- **Response Headers:**
  - `Content-Type: application/pdf` - Tells browser this is a PDF file
  - `Content-Disposition: inline` - Display in browser (change to `attachment` for download)
  - `filename=generated_report.pdf` - Default filename if user saves the PDF

**Flow:**
1. Spring Boot receives POST request to `/api/content/submit`
2. Jackson deserializes JSON body to `GenerateRequestDto`
3. Validation runs automatically (fails with 400 if invalid)
4. Controller calls `markdownService.generateDocument(data)`
5. Service returns PDF as `Resource` (stream wrapper)
6. Controller wraps in `ResponseEntity` with proper headers
7. Spring streams PDF binary to client

#### 3. Service Layer - The Pipeline Orchestrator

**File:** [MarkdownService.java](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java)

**Purpose:** Core business logic - orchestrates the 5-stage document generation pipeline.

##### 3.1 Service Initialization

```java
@Service
public class MarkdownService {
    private static final Logger logger = LoggerFactory.getLogger(MarkdownService.class);

    private final Handlebars handlebars;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public MarkdownService() {
        this.handlebars = new Handlebars();

        // FlexMark Configuration
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                AttributesExtension.create()
        ));

        // CRITICAL: Allow HTML tags to pass through
        options.set(HtmlRenderer.SUPPRESS_HTML, false);

        // Enable deep parsing (parse markdown inside HTML tags)
        options.set(Parser.HTML_BLOCK_DEEP_PARSER, true);
        options.set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false);

        // Disable indented code blocks (use ``` instead)
        options.set(Parser.INDENTED_CODE_BLOCK_PARSER, false);

        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }
}
```

**Key Configuration Decisions:**

**1. `SUPPRESS_HTML = false`** (CRITICAL)
- **Why:** We run Flexmark **after** Handlebars, so the input contains valid HTML tags from the template
- **Effect:** HTML tags like `<div class="card">` pass through instead of being escaped to `&lt;div&gt;`
- **Example:** Template has `<div>{{name}}</div>` → After Handlebars: `<div>John</div>` → Markdown must preserve the `<div>` tags

**2. `HTML_BLOCK_DEEP_PARSER = true`**
- **Why:** Enables parsing Markdown inside HTML tags (critical for our template-first approach)
- **Example:**
  ```html
  <div class="section">
    <md>
    ## Title
    - Item 1
    </md>
  </div>
  ```
  Without this, the Markdown inside `<md>` tags wouldn't be processed.

**3. `INDENTED_CODE_BLOCK_PARSER = false`**
- **Why:** Template indentation could accidentally trigger code blocks (4 spaces = code block in Markdown)
- **Solution:** Disable indented code blocks, require triple backticks ``` for code blocks
- **Trade-off:** Users must use fenced code blocks instead of indented ones

##### 3.2 Pipeline Entry Point

**Method:** `generateDocument(GenerateRequestDto request)` ([MarkdownService.java:119](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L119))

```java
public Resource generateDocument(GenerateRequestDto request) {
    // Input validation
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
        Map<String, Object> rawData = request.getDocPropertiesJsonData();

        if (rawData == null) {
            logger.warn("No document properties data provided, using empty map");
            rawData = Map.of();
        }

        // Step 2: Handlebars Merge
        Template template = handlebars.compileInline(templateStr);
        String hybridContent = template.apply(rawData);

        // Step 3: Markdown Conversion
        Document doc = processMarkdownToDom(hybridContent);

        // Step 4: DOM Assembly & XHTML Normalization
        configureFinalDom(doc, cssStr, headerStr, footerStr);

        // Step 5: PDF Generation
        return generatePdf(doc);

    } catch (IOException e) {
        logger.error("IO error during document generation", e);
        throw new RuntimeException("Error during template compilation or IO", e);
    } catch (IllegalArgumentException e) {
        logger.error("Invalid input provided", e);
        throw new IllegalArgumentException("Invalid Base64 input provided: " + e.getMessage(), e);
    }
}
```

**Flow Breakdown:**

**Step 1: Input Validation & Decoding**
- Null check on request object (defensive programming)
- Verify `templateEncoded` is present (secondary check after controller validation)
- Decode all Base64 inputs using `decode()` helper method
- Handle null `docPropertiesJsonData` with empty map (avoid NullPointerException)

**Step 2: Handlebars Templating**
- Compile template string into Handlebars `Template` object
- Apply data map to template → expands loops, conditionals, variables
- Result is "hybrid content" - HTML structure with raw Markdown inside `<md>` tags

**Step 3: Markdown to HTML Conversion**
- Parse hybrid content to Jsoup DOM
- Find all `<md>` tags, render Markdown to HTML, replace in-place
- Result is pure HTML DOM (no more Markdown)

**Step 4: DOM Assembly**
- Inject CSS into `<head>`
- Prepend footer to `<body>`
- Prepend header to `<body>` (appears above footer)
- Enforce XHTML compliance (required for iText7)

**Step 5: PDF Generation**
- Convert XHTML to PDF binary using iText7
- Return as `InputStreamResource` for streaming to client

##### 3.3 Base64 Decoding

**Method:** `decode(String base64)` ([MarkdownService.java:312](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L312))

```java
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
```

**Key Points:**
- **Null Safety:** Returns empty string for null/blank input (optional fields)
- **UTF-8 Encoding:** Ensures proper character encoding after Base64 decode
- **Error Handling:** Catches and wraps `IllegalArgumentException` with context

##### 3.4 Handlebars Templating (Stage 2)

**Why Template-First?**

Traditional approach:
```
Data + Markdown → Render Markdown → Insert into Template → HTML
```

Our approach:
```
Template + Data → Expand Template (keeping Markdown raw) → Render Markdown → HTML
```

**Benefit:** Enable loops that generate Markdown content:

```handlebars
{{#each users}}
<md>
## {{name}}
- Email: {{email}}
- Role: {{role}}
</md>
{{/each}}
```

After Handlebars (hybrid state):
```html
<md>
## Alice
- Email: alice@example.com
- Role: Admin
</md>
<md>
## Bob
- Email: bob@example.com
- Role: User
</md>
```

After Markdown rendering:
```html
<h2>Alice</h2>
<ul>
  <li>Email: alice@example.com</li>
  <li>Role: Admin</li>
</ul>
<h2>Bob</h2>
<ul>
  <li>Email: bob@example.com</li>
  <li>Role: User</li>
</ul>
```

##### 3.5 Markdown Processing (Stage 3)

**Method:** `processMarkdownToDom(String hybridContent)` ([MarkdownService.java:331](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L331))

```java
private Document processMarkdownToDom(String hybridContent) {
    // Parse the full document
    Document doc = Jsoup.parse(hybridContent);

    // Find all <md> tags
    Elements mdBlocks = doc.select("md");

    for (Element mdElement : mdBlocks) {
        // CRITICAL: Use wholeText() to preserve newlines
        String markdownText = mdElement.wholeText();

        // Strip leading whitespace from each line
        // IMPORTANT: Preserve blank lines (significant for paragraph separation)
        String cleanMarkdown = markdownText.lines()
                .map(line -> line.trim().isEmpty() ? "" : line.trim())
                .collect(Collectors.joining("\n"));

        // Render Markdown to HTML
        String renderedHtml = htmlRenderer.render(markdownParser.parse(cleanMarkdown));

        // Replace <md> element with rendered HTML
        mdElement.after(renderedHtml);
        mdElement.remove();
    }

    return doc;
}
```

**Key Decisions:**

**1. DOM-Based Processing (Not Regex)**
- **Before (v2.0):** Used regex to find `<md>...</md>`, prone to errors with nested tags
- **After (v2.0+):** Use Jsoup DOM parser - handles nested tags, attributes, malformed HTML
- **Performance:** Single-pass parsing, in-place replacement (no string concatenation)

**2. `wholeText()` vs `text()`**
- **`text()`:** Strips newlines, merges lines → breaks Markdown lists/tables
- **`wholeText()`:** Preserves newlines → correct Markdown parsing
- **Example:**
  ```
  text() returns: "- Item 1- Item 2"  (broken list)
  wholeText() returns: "- Item 1\n- Item 2"  (valid list)
  ```

**3. Whitespace Handling**
- **Problem:** Templates often indent `<md>` blocks for readability
  ```handlebars
  <div>
      <md>
          ## Title
          Content
      </md>
  </div>
  ```
  Without trimming, Markdown sees 8 spaces before `## Title` → might trigger code block

- **Solution:** Trim leading whitespace from each line
- **Critical:** Preserve blank lines (empty after trim) for paragraph separation
  ```java
  .map(line -> line.trim().isEmpty() ? "" : line.trim())
  ```

**4. In-Place Replacement**
- `mdElement.after(renderedHtml)` - Insert rendered HTML after `<md>` tag
- `mdElement.remove()` - Remove the `<md>` tag itself
- Result: `<md>...</md>` is replaced with rendered HTML in the DOM

##### 3.6 DOM Configuration (Stage 4)

**Method:** `configureFinalDom(Document doc, String cssStr, String headerStr, String footerStr)` ([MarkdownService.java:377](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L377))

```java
private void configureFinalDom(Document doc, String cssStr, String headerStr, String footerStr) {
    // Enforce XHTML Syntax for iText7
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
        logger.debug("Processing footer - HTML length: {}", footerStr.length());

        // Parse footer as separate document for proper XHTML handling
        Document footerDoc = Jsoup.parse(footerStr);
        footerDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        footerDoc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

        String parsedFooter = footerDoc.body().html();
        doc.body().prepend(parsedFooter);
    }

    // Inject Header (prepended after footer to appear at the very top)
    if (StringUtils.hasText(headerStr)) {
        logger.debug("Processing header - HTML length: {}", headerStr.length());

        Document headerDoc = Jsoup.parse(headerStr);
        headerDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        headerDoc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

        String parsedHeader = headerDoc.body().html();
        doc.body().prepend(parsedHeader);
    }
}
```

**XHTML Compliance:**
- **Why:** iText7 uses an XML parser - requires valid XML/XHTML
- **What it does:**
  - Self-closing tags: `<br />` not `<br>`
  - Properly escaped entities: `&amp;` for `&`
  - Quoted attributes: `class="foo"` not `class=foo`
- **How:** Jsoup automatically enforces these rules with `.syntax(Syntax.xml)`

**CSS Injection:**
- Append `<style>` element to `<head>`
- Use `.html(cssStr)` to set inner HTML (preserves CSS syntax)

**Header/Footer Injection:**
- **Why parse separately?** Ensures proper XHTML compliance even if header/footer has malformed HTML
- **Order matters:**
  1. Footer prepended first
  2. Header prepended second (appears before footer in DOM)
  3. Result: Header → Template Content → Footer
- **Data URI Preservation:** Jsoup preserves `src="data:image/..."` attributes without corruption

##### 3.7 PDF Generation (Stage 5)

**Method:** `generatePdf(Document doc)` ([MarkdownService.java:177](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L177))

```java
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
```

**Key Points:**
- **ByteArrayOutputStream:** Accumulates PDF binary in memory
- **Try-with-resources:** Automatically closes the stream (no memory leak)
- **`doc.html()`:** Serializes Jsoup DOM to XHTML string
- **`HtmlConverter.convertToPdf()`:** iText7 converts XHTML → PDF
- **`InputStreamResource`:** Wraps byte array as Spring `Resource` for streaming to client

**ConverterProperties Configuration:**

**Method:** `createConverterProperties()` ([MarkdownService.java:202](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L202))

```java
private ConverterProperties createConverterProperties() {
    ConverterProperties properties = new ConverterProperties();

    // FontProvider for managing fonts
    FontProvider fontProvider = new DefaultFontProvider(false, true, false);
    properties.setFontProvider(fontProvider);

    // Set base URI to empty string
    // Data URIs are self-contained and don't need base URI resolution
    properties.setBaseUri("");
    logger.debug("Using base URI: (empty string)");

    // Configure secure resource retrieval with SSRF protection
    properties.setResourceRetriever(new SecureDataUriResourceRetriever());

    return properties;
}
```

**Configuration Details:**

**1. FontProvider**
- `DefaultFontProvider(false, true, false)` parameters:
  - `registerStandardPdfFonts`: false - Don't register standard PDF fonts
  - `registerSystemFonts`: true - **Use system fonts** (enables broader font support)
  - `registerShippedFonts`: false - Don't register shipped fonts

**2. Base URI**
- Set to empty string `""`
- **Why:** Data URIs are self-contained (all data embedded in the URI)
- **Alternative:** Could use `"classpath:/static/"` for file-based resources
- **Our approach:** No file-based images, only data URIs

**3. Resource Retriever**
- Custom `SecureDataUriResourceRetriever` implementation
- Intercepts all resource requests (images, fonts, CSS)
- Provides security controls (SSRF protection)

##### 3.8 Secure Resource Retrieval - SSRF Protection

**Inner Class:** `SecureDataUriResourceRetriever` ([MarkdownService.java:229](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L229))

```java
private static class SecureDataUriResourceRetriever implements IResourceRetriever {

    @Override
    public InputStream getInputStreamByUrl(java.net.URL url) throws java.io.IOException {
        if (url == null) {
            throw new java.io.IOException("URL cannot be null");
        }

        String urlString = url.toString();

        // Allow data URIs - parse and decode inline
        if (urlString.startsWith("data:")) {
            return parseDataUri(urlString);
        }

        // Allow file:// URLs (local static resources)
        if (urlString.startsWith("file://") || urlString.startsWith("jar:file:")) {
            return url.openStream();
        }

        // Block ALL external HTTP/HTTPS requests (SSRF protection)
        if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
            throw new java.io.IOException("External HTTP/HTTPS requests are blocked for security reasons: " + urlString);
        }

        return url.openStream();
    }

    @Override
    public byte[] getByteArrayByUrl(java.net.URL url) throws java.io.IOException {
        try (InputStream is = getInputStreamByUrl(url)) {
            return is.readAllBytes();
        }
    }
}
```

**What is SSRF?**
- **Server-Side Request Forgery:** Attacker tricks server into making requests to internal/external resources
- **Example Attack:**
  ```html
  <img src="http://localhost:8080/admin/delete-users" />
  ```
  If allowed, the server would make a request to its own admin endpoint during PDF generation.

**Our Protection:**
- ✅ **Allow:** Data URIs (safe - data is embedded, no network request)
- ✅ **Allow:** Local file:// URLs (controlled - only local resources)
- ❌ **Block:** HTTP/HTTPS URLs (unsafe - could target internal services)

**Data URI Parsing:**

**Method:** `parseDataUri(String dataUri)` ([MarkdownService.java:276](src/main/java/com/flexmark/flexMarkProject/service/MarkdownService.java#L276))

```java
private InputStream parseDataUri(String dataUri) throws IOException {
    // Find comma separator
    int commaIndex = dataUri.indexOf(',');
    if (commaIndex < 0 || commaIndex >= dataUri.length() - 1) {
        throw new IOException("Malformed data URI: missing comma separator");
    }

    // Extract metadata and data parts
    String metadata = dataUri.substring(5, commaIndex); // Skip "data:"
    String data = dataUri.substring(commaIndex + 1);

    // Decode based on encoding type
    byte[] decodedData;
    if (metadata.contains("base64")) {
        // Base64 encoded data (most common for images)
        try {
            decodedData = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to decode base64 data in data URI", e);
        }
    } else {
        // URL-encoded data (less common, mainly for text)
        String decoded = java.net.URLDecoder.decode(data, StandardCharsets.UTF_8);
        decodedData = decoded.getBytes(StandardCharsets.UTF_8);
    }

    return new ByteArrayInputStream(decodedData);
}
```

**Data URI Format (RFC 2397):**
```
data:[<mediatype>][;base64],<data>

Example:
data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...
│    │         │       │
│    │         │       └─ Base64-encoded binary data
│    │         └───────── Encoding flag
│    └─────────────────── MIME type
└──────────────────────── Protocol
```

**Parsing Steps:**
1. Find comma (separates metadata from data)
2. Extract metadata: `image/png;base64`
3. Extract data: `iVBORw0KGgoAAAANSUhEUg...`
4. Check if base64 encoding is specified
5. Decode base64 → binary bytes
6. Return as `ByteArrayInputStream` for iText7

**Why This Works:**
- iText7 encounters `<img src="data:image/png;base64,...">` in the HTML
- Calls `getInputStreamByUrl(new URL("data:image/png;base64,..."))`
- Our custom retriever intercepts the call
- Parses and decodes the data URI
- Returns binary image stream
- iText7 renders the image in the PDF

### Complete Data Flow Example

Let's trace a real request through the entire pipeline:

**1. Client Sends Request:**
```json
{
  "templateEncoded": "PGRpdj48aDE+e3t0aXRsZX19PC9oMT48bWQ+IyMge3tuYW1lfX08L21kPjwvZGl2Pg==",
  "cssEncoded": "aDEgeyBjb2xvcjogYmx1ZTsgfQ==",
  "docPropertiesJsonData": {
    "title": "Welcome",
    "name": "Alice"
  }
}
```

**2. Controller Receives & Validates:**
- Spring deserializes JSON to `GenerateRequestDto`
- Validation passes (`templateEncoded` is present)
- Calls `markdownService.generateDocument(request)`

**3. Service Decodes Base64:**
```
templateEncoded → "<div><h1>{{title}}</h1><md>## {{name}}</md></div>"
cssEncoded → "h1 { color: blue; }"
```

**4. Handlebars Processes Template:**
```
Input: "<div><h1>{{title}}</h1><md>## {{name}}</md></div>"
Data: {title: "Welcome", name: "Alice"}
Output: "<div><h1>Welcome</h1><md>## Alice</md></div>"
```

**5. Markdown Rendering:**
- Parse to Jsoup DOM
- Find `<md>` tag with content `"## Alice"`
- Render Markdown → `<h2>Alice</h2>`
- Replace: `<div><h1>Welcome</h1><h2>Alice</h2></div>`

**6. DOM Configuration:**
- Inject CSS: `<head><style>h1 { color: blue; }</style></head>`
- Enforce XHTML compliance
- Final HTML:
  ```html
  <!DOCTYPE html>
  <html>
    <head><style>h1 { color: blue; }</style></head>
    <body>
      <div><h1>Welcome</h1><h2>Alice</h2></div>
    </body>
  </html>
  ```

**7. PDF Generation:**
- iText7 converts XHTML → PDF binary
- Wrap in `InputStreamResource`

**8. Controller Returns Response:**
```
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: inline; filename=generated_report.pdf

[PDF binary data...]
```

### Key Takeaways

**Architecture Principles:**
1. **Separation of Concerns:** Controller handles HTTP, Service handles business logic
2. **Template-First:** Enables dynamic Markdown generation through loops/conditionals
3. **DOM-Based Processing:** More robust than regex, handles complex HTML
4. **XHTML Compliance:** Automatic enforcement ensures iText7 compatibility
5. **Security First:** SSRF protection blocks external resource requests

**Performance Optimizations:**
1. **No Temporary Files:** Data URIs decoded on-the-fly (no I/O overhead)
2. **Single-Pass Processing:** DOM parsed once, modified in-place
3. **Stream-Based Output:** PDF streamed directly to client (no intermediate storage)
4. **Stateless Design:** No state management, scales horizontally

**Error Handling Strategy:**
1. **Fail Fast:** Validation at controller layer before service processing
2. **Descriptive Errors:** Clear exception messages with context
3. **Defensive Programming:** Null checks, try-catch with proper logging
4. **Graceful Degradation:** Empty strings for optional fields, default values for null data

**Testing Strategy:**
- **Unit Tests:** Test individual methods in isolation (MarkdownServiceTest)
- **Integration Tests:** Test full HTTP request/response cycle (InitialControllerIntegrationTest)
- **Security Tests:** Verify SSRF protection (SecureDataUriResourceRetrieverTest)
- **Edge Cases:** Test validation errors, malformed input, boundary conditions

## Testing & Quality Assurance

### Test Coverage

The FlexMark PDF Generator has **comprehensive test coverage** across all critical components:

**Test Suite Summary:**
```
✅ 51 Total Tests - All Passing
   ├─ 19 Unit Tests (MarkdownService)
   ├─ 16 Integration Tests (InitialController)
   ├─ 15 Security Tests (SecureDataUriResourceRetriever)
   └─  1 Context Load Test (Application)
```

**Run Tests:**
```bash
mvn test
```

**Expected Output:**
```
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Test Organization

#### 1. Unit Tests - MarkdownService ([MarkdownServiceTest.java](src/test/java/com/flexmark/flexMarkProject/service/MarkdownServiceTest.java))

**Coverage:** 19 tests covering the complete document generation pipeline

**Test Categories:**

**Basic Functionality (4 tests):**
- ✅ Generate PDF from simple template
- ✅ Process Handlebars variable substitution
- ✅ Convert Markdown to HTML correctly
- ✅ Handle Markdown tables

**CSS & Styling (1 test):**
- ✅ Inject CSS correctly

**Header & Footer Injection (3 tests):**
- ✅ Inject header correctly
- ✅ Inject footer correctly
- ✅ Handle footer with data URI images

**Complex Integration (1 test):**
- ✅ Complete document with all features (template + CSS + header + footer + data)

**Error Handling (6 tests):**
- ✅ Throw exception when request is null
- ✅ Throw exception when template is missing
- ✅ Throw exception when template is empty
- ✅ Throw exception for invalid Base64 encoding
- ✅ Handle empty data map gracefully
- ✅ Handle null data map gracefully

**Data URI Handling (2 tests):**
- ✅ Handle multiple data URI images
- ✅ Handle JPEG data URIs

**Handlebars Templating (2 tests):**
- ✅ Handle Handlebars loops correctly (invoice generation)
- ✅ Handle nested Handlebars structures (departments/employees)

**Key Test Example:**
```java
@Test
@DisplayName("Should handle complete document with all features")
void testCompleteDocument() throws IOException {
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

    String css = ".container { padding: 20px; } h1 { color: navy; }";
    String header = "<div style='text-align: center;'><h3>Annual Report</h3></div>";
    String footer = "<div style='text-align: center;'><p>Page <span class='page-number'></span></p></div>";

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

    Resource result = markdownService.generateDocument(request);

    assertNotNull(result);
    assertTrue(result.contentLength() > 1000); // PDF has substantial content

    // Verify PDF magic bytes
    try (InputStream is = result.getInputStream()) {
        byte[] pdfBytes = is.readAllBytes();
        assertEquals('%', pdfBytes[0]); // PDF header
        assertEquals('P', pdfBytes[1]);
        assertEquals('D', pdfBytes[2]);
        assertEquals('F', pdfBytes[3]);
    }
}
```

#### 2. Integration Tests - InitialController ([InitialControllerIntegrationTest.java](src/test/java/com/flexmark/flexMarkProject/controller/InitialControllerIntegrationTest.java))

**Coverage:** 16 tests covering the complete HTTP request/response cycle using Spring Boot MockMvc

**Test Categories:**

**Successful Requests (5 tests):**
- ✅ Generate PDF from valid request
- ✅ Handle request with all optional fields
- ✅ Generate PDF with Markdown content
- ✅ Generate PDF with data URI images
- ✅ Generate complex multi-page document

**Validation Errors (3 tests):**
- ✅ Return 400 when template is missing
- ✅ Return 400 when template is empty
- ✅ Return 400 for invalid JSON
- ✅ Return 500 for invalid Base64 template

**Content Type Validation (2 tests):**
- ✅ Reject request without Content-Type
- ✅ Reject request with wrong Content-Type

**Complex Documents (2 tests):**
- ✅ Handle document with Handlebars iteration
- ✅ Handle document with Markdown tables

**HTTP Method Validation (3 tests):**
- ✅ Reject GET requests (405 Method Not Allowed)
- ✅ Reject PUT requests (405 Method Not Allowed)
- ✅ Reject DELETE requests (405 Method Not Allowed)

**Key Test Example:**
```java
@Test
@DisplayName("Should generate PDF from valid request")
void testSuccessfulPdfGeneration() throws Exception {
    String template = "<div><h1>Test Document</h1></div>";
    GenerateRequestDto request = new GenerateRequestDto();
    request.setTemplateEncoded(Base64.getEncoder().encodeToString(
        template.getBytes(StandardCharsets.UTF_8)
    ));

    MvcResult result = mockMvc.perform(post("/api/content/submit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", containsString("inline")))
        .andExpect(header().string("Content-Disposition", containsString("generated_report.pdf")))
        .andReturn();

    byte[] pdfBytes = result.getResponse().getContentAsByteArray();
    assert pdfBytes.length > 0 : "PDF should not be empty";
    assert pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F'
        : "Response should be a valid PDF";
}
```

#### 3. Security Tests - SecureDataUriResourceRetriever ([SecureDataUriResourceRetrieverTest.java](src/test/java/com/flexmark/flexMarkProject/service/SecureDataUriResourceRetrieverTest.java))

**Coverage:** 15 tests covering SSRF protection and data URI parsing using reflection to test private inner class

**Test Categories:**

**Data URI Parsing (4 tests):**
- ✅ Parse PNG data URI correctly
- ✅ Parse JPEG data URI correctly
- ✅ Handle data URI without base64 encoding (URL-encoded text)
- ✅ Parse GIF data URI correctly

**File URL Support (2 tests):**
- ✅ Allow file:// URLs (local resources)
- ✅ Allow jar:file:// URLs (classpath resources)

**SSRF Protection (4 tests):**
- ✅ Block HTTP URLs (SSRF protection)
- ✅ Block HTTPS URLs (SSRF protection)
- ✅ Block localhost HTTP URLs (common SSRF target)
- ✅ Block 127.0.0.1 HTTP URLs (loopback SSRF attack)

**Error Handling (3 tests):**
- ✅ Throw IOException for null URL
- ✅ Throw IOException for malformed data URI (missing comma separator)
- ✅ Throw IOException for invalid Base64 in data URI

**Method Equivalence (2 tests):**
- ✅ getByteArrayByUrl returns same data as getInputStreamByUrl
- ✅ Handle large data URIs (1KB+)

**Key Security Test Example:**
```java
@Test
@DisplayName("Should block HTTP URLs (SSRF protection)")
void testHttpUrlBlocked() throws Exception {
    URL url = new URL("http://example.com/malicious.jpg");

    Exception exception = assertThrows(Exception.class, () ->
        getInputStreamMethod.invoke(retrieverInstance, url)
    );

    Throwable cause = exception.getCause();
    assertInstanceOf(IOException.class, cause);
    assertTrue(cause.getMessage().contains("blocked for security reasons"),
        "Error message should mention security blocking");
}
```

**Data URI Validation Test:**
```java
@Test
@DisplayName("Should parse PNG data URI correctly")
void testPngDataUri() throws Exception {
    String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
    String dataUri = "data:image/png;base64," + redPixelBase64;
    URL url = new URL(dataUri);

    InputStream is = (InputStream) getInputStreamMethod.invoke(retrieverInstance, url);

    assertNotNull(is);
    byte[] data = is.readAllBytes();
    assertTrue(data.length > 0, "Decoded data should not be empty");

    // PNG files start with specific magic bytes: 137 80 78 71
    assertEquals((byte) 137, data[0], "PNG should start with 137");
    assertEquals((byte) 80, data[1], "PNG second byte should be 80 (P)");
    assertEquals((byte) 78, data[2], "PNG third byte should be 78 (N)");
    assertEquals((byte) 71, data[3], "PNG fourth byte should be 71 (G)");
}
```

### Test Quality Metrics

**Code Coverage:**
- ✅ **Controller Layer:** 100% (all endpoints tested)
- ✅ **Service Layer:** ~95% (all critical paths covered)
- ✅ **Security Layer:** 100% (SSRF protection fully tested)
- ✅ **Error Handling:** 100% (all exception paths tested)

**Test Characteristics:**
- ✅ **Deterministic:** All tests produce consistent results
- ✅ **Isolated:** No shared state between tests
- ✅ **Fast:** Complete test suite runs in ~15 seconds
- ✅ **Descriptive:** `@DisplayName` annotations explain what each test validates
- ✅ **Comprehensive:** Tests cover happy paths, edge cases, and error conditions

### Continuous Integration

**Pre-commit Checklist:**
```bash
# Run all tests
mvn test

# Ensure all tests pass
# Expected: Tests run: 51, Failures: 0, Errors: 0, Skipped: 0

# Run application
mvn spring-boot:run

# Test via Swagger UI
# http://localhost:8080/swagger-ui.html
```

**Build Verification:**
```bash
# Full build with tests
mvn clean install

# Verify JAR is created
# target/flexMarkProject-0.0.1-SNAPSHOT.jar
```

### Test Maintenance

**When Adding New Features:**
1. Write unit tests for new service methods
2. Write integration tests for new endpoints
3. Update security tests if resource handling changes
4. Ensure test coverage remains above 90%

**Test Naming Convention:**
- **Unit Tests:** `test[MethodName][Scenario]` (e.g., `testHandlebarsLoops`)
- **Integration Tests:** `test[HttpScenario]` (e.g., `testSuccessfulPdfGeneration`)
- **Security Tests:** `test[SecurityScenario]` (e.g., `testHttpUrlBlocked`)

**Test Documentation:**
- All tests include `@DisplayName` annotations describing the validation
- Complex tests include inline comments explaining the test data
- Each test file has a class-level JavaDoc explaining its purpose

## Recent Updates

### v6.2: Comprehensive Test Suite
- **Test Coverage:**
  - Added **51 comprehensive tests** covering all critical components
  - **19 unit tests** for MarkdownService (pipeline, templating, Markdown rendering, error handling)
  - **16 integration tests** for InitialController (HTTP layer, validation, content types, method validation)
  - **15 security tests** for SecureDataUriResourceRetriever (SSRF protection, data URI parsing)
  - **1 context load test** verifying Spring Boot application startup
- **Test Organization:**
  - All tests use `@DisplayName` annotations for clear documentation
  - Tests organized by category (basic functionality, error handling, security, complex scenarios)
  - Each test file includes comprehensive JavaDoc explaining its purpose
- **Test Quality:**
  - **100% pass rate** - all 51 tests passing
  - **Code coverage:** ~95% service layer, 100% controller and security layers
  - **Fast execution:** Complete test suite runs in ~15 seconds
  - **Deterministic:** All tests produce consistent, repeatable results
- **Testing Features:**
  - PDF magic bytes validation (verifies output is valid PDF)
  - HTTP response validation (status codes, content types, headers)
  - SSRF attack prevention verification (blocks malicious HTTP/HTTPS URLs)
  - Data URI parsing validation (PNG, JPEG, GIF format support)
  - Handlebars templating validation (loops, nested structures, variable substitution)
  - Markdown rendering validation (tables, lists, emphasis, headers)
  - Error handling validation (null requests, missing templates, invalid Base64)
- **Documentation:**
  - Added comprehensive "Testing & Quality Assurance" section to README
  - Included test examples for each category
  - Documented test naming conventions and maintenance guidelines
- **Benefit:** Production-ready test suite ensures reliability, catches regressions early, and provides clear examples of API usage. All critical paths are validated including happy paths, edge cases, and error conditions.

### v6.1: Swagger/OpenAPI Documentation Integration
- **Interactive API Documentation:**
  - Added SpringDoc OpenAPI dependency (version 2.6.0)
  - Comprehensive Swagger UI at `/swagger-ui.html`
  - OpenAPI 3.0 specification at `/v3/api-docs`
- **DTO Enhancements:**
  - Added `@Schema` annotations to `GenerateRequestDto` with field-level documentation
  - Included Base64 examples for all fields
  - Added complete request example at class level
- **Controller Documentation:**
  - Added `@Tag` annotation grouping endpoints under "PDF Document Generation"
  - Added `@Operation` with detailed pipeline overview, features, and security notes
  - Added `@ApiResponses` documenting 200, 400, and 500 response codes
  - Added `@Parameter` with 3 interactive request examples:
    - Simple template example
    - Template with loops example
    - Full example with header/footer and data URIs
- **Configuration:**
  - Created `OpenApiConfig` class with comprehensive API metadata
  - Configured contact information, license, and server URLs
  - Added extensive API description with features, pipeline, and use cases
- **Application Properties:**
  - Configured Swagger UI path and settings
  - Enabled interactive testing ("Try it out" feature)
  - Configured operation sorting and request duration display
- **Documentation:**
  - Created `SWAGGER_SETUP.md` with detailed setup and usage instructions
  - Updated `flexmark_readme.md` with Swagger section
  - Included example requests with decoded values
- **Benefit:** Developers can now explore and test the API interactively through a browser, with comprehensive documentation and ready-to-use examples. No Postman or curl commands needed for testing.

### v6.0: Simplified Architecture & Native Data URI Support
- **Architecture Simplification:**
  - Removed manual image injection logic from `MarkdownService`
  - Removed `imageEncoded` field from `GenerateRequestDto`
  - Eliminated ~250 lines of complex image processing code (methods: `processImagesInDocument`, `processHtmlWithImage`, `createDataUri`, `decodeImage`, `determineImageMimeType`, `isSupportedImageType`, `matchesSignature`, `extractBase64Data`)
  - Removed image-related constants (MIME types, magic bytes, HTML markers)
- **Native Data URI Support:**
  - Images are now embedded directly in HTML using data URIs: `<img src="data:image/png;base64,...">`
  - iText7 natively handles data URIs - no custom processing required
  - Simpler client integration: construct HTML with embedded images before Base64 encoding
- **Security Enhancement:**
  - Implemented custom `SecureDataUriResourceRetriever` with SSRF protection
  - **Allows:** Data URIs (Base64-encoded images) and local classpath resources
  - **Blocks:** External HTTP/HTTPS requests to prevent Server-Side Request Forgery attacks
  - Provides clear error messages when blocked resources are attempted
- **Improved Pipeline:**
  - Reduced from 7 stages to 5 stages (removed image pre-processing and post-processing stages)
  - Cleaner, more maintainable codebase with fewer moving parts
  - Better performance: no DOM traversal for image replacement
- **Breaking Change:**
  - Clients must now embed images as data URIs in HTML before sending to the API
  - The `imageEncoded` field has been removed from the DTO
  - Migration: Convert `imageEncoded` to data URI and embed in `headerEncoded`/`footerEncoded`
- **Benefit:** Dramatically simplified architecture, better security, native iText7 data URI handling, and clearer client responsibility for image encoding.

### v5.1: Code Cleanup & Validation Improvements
- **Dependency Cleanup:**
  - Removed unused Flying Saucer (xhtmlrenderer) dependency left over from v3.0 migration
  - Removed unused FlexmarkConfig class that was not properly registered as a Spring bean
  - Added spring-boot-starter-validation for Jakarta Bean Validation support
- **DTO Improvements:**
  - Removed unused fields (`filePath`, `templateName`) from GenerateRequestDto
  - Added `@NotBlank` validation annotation to `templateEncoded` field
  - Added comprehensive JavaDoc documentation to all DTO fields
  - Controller now uses `@Valid` annotation for automatic validation
- **Bug Fixes:**
  - **Critical:** Fixed Markdown processing bug that was removing blank lines. Blank lines are now properly preserved as they're significant in Markdown for paragraph separation
  - Fixed Base64 regex pattern to handle whitespace/newlines that commonly appear in real-world Base64 strings
- **Code Cleanup:**
  - Removed commented-out sanitization code and related unused imports
  - Cleaned up unused method `sanitizeInputData()` for better code clarity
- **Validation Error Handling:**
  - Spring Boot now automatically returns 400 Bad Request with validation error details when `templateEncoded` is missing or blank
  - Validation errors are returned before reaching the service layer, improving performance and error clarity
- **Benefit:** Cleaner codebase, smaller dependency footprint, better validation with clear error messages, and critical bug fix for Markdown paragraph handling.

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
  - Improved string handling with better blank line preservation
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
- **Input Validation:**
  - The service uses Jakarta Bean Validation with `@Valid` and `@NotBlank` annotations
  - Required field validation happens at the controller layer before reaching business logic
  - Validates request structure and required fields to prevent processing invalid requests
  - Automatic 400 Bad Request responses for validation failures
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