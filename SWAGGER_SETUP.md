# Swagger/OpenAPI Documentation Setup

This document describes the Swagger/OpenAPI configuration for the FlexMark PDF Generator API.

## Quick Start

### 1. Install Dependencies

The Swagger dependency has been added to `pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

### 4. Access Swagger UI

Once the application is running, access the interactive API documentation at:

**Swagger UI:** http://localhost:8080/swagger-ui.html

**OpenAPI JSON:** http://localhost:8080/v3/api-docs

## What Was Added

### 1. Dependencies (`pom.xml`)
- Added `springdoc-openapi-starter-webmvc-ui` for Spring Boot 3+ OpenAPI support

### 2. DTO Annotations (`GenerateRequestDto.java`)
- `@Schema` on class level with full example JSON
- `@Schema` on each field with descriptions, examples, and required status
- Detailed field-level documentation for Swagger UI

### 3. Controller Annotations (`InitialController.java`)
- `@Tag` - Groups endpoints under "PDF Document Generation"
- `@Operation` - Detailed endpoint description with pipeline overview and features
- `@ApiResponses` - Documents all response codes (200, 400, 500)
- `@Parameter` - Documents request body with multiple examples
- `@ExampleObject` - Provides 3 real-world examples:
  - Simple template
  - Template with loops
  - Full example with header/footer

### 4. Configuration Class (`OpenApiConfig.java`)
- Comprehensive API metadata
- Contact information
- License details
- Server configurations (local & production)
- Extensive API description with features, pipeline, use cases

### 5. Application Properties (`application.properties`)
- Swagger UI path configuration
- Try-it-out enabled for testing
- Request duration display
- Sorted operations and tags

## Swagger UI Features

### Interactive Testing

The Swagger UI allows you to:

1. **View All Endpoints** - See the complete API structure
2. **Try It Out** - Test endpoints directly from the browser
3. **Example Requests** - Copy/paste ready-to-use JSON examples
4. **Response Schemas** - Understand response structures
5. **Error Documentation** - See all possible error responses

### Example Workflow

1. Navigate to http://localhost:8080/swagger-ui.html
2. Expand "PDF Document Generation" tag
3. Click on `POST /api/content/submit`
4. Click "Try it out"
5. Select an example from the dropdown:
   - "Simple template"
   - "Template with loops"
   - "Full example with header/footer"
6. Click "Execute"
7. View the response (PDF binary or error message)

## Available Examples

### Example 1: Simple Template
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
**Decoded CSS:** `h1 { color: blue; }`

### Example 2: Template with Loops
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
**Decoded template:** `<div><h1>{{title}}</h1>{{#each items}}<md>| Product | Price |\n|---|---|\n| {{name}} | ${{price}} |</md>{{/each}}</div>`

### Example 3: Full Example with Header/Footer
```json
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
```
**Decoded template:** `<div><h1>{{title}}</h1><md>## Report Summary\n- Total: {{total}}</md></div>`
**Decoded CSS:** `h1 { color: #333; font-size: 24px; }`
**Decoded header:** `<div id="header"><img src="data:image/png;base64,iVBORw0KGgo..." /></div>`
**Decoded footer:** `<div id="footer">Page <span class="page-number"></span></div>`

## Response Documentation

### Success Response (200 OK)
```
Content-Type: application/pdf
Content-Disposition: inline; filename=generated_report.pdf

[PDF binary data]
```

### Error Responses

#### 400 Bad Request
```json
{
  "timestamp": "2025-12-26T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Template is required and cannot be empty",
  "path": "/api/content/submit"
}
```

#### 500 Internal Server Error
```json
{
  "timestamp": "2025-12-26T10:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Error during template compilation or IO",
  "path": "/api/content/submit"
}
```

## Configuration Options

### application.properties

The following Swagger configuration is available in `application.properties`:

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
springdoc.show-actuator=false
```

### Configuration Explanation

- `springdoc.api-docs.path` - Path to OpenAPI JSON specification
- `springdoc.swagger-ui.path` - Path to Swagger UI interface
- `springdoc.swagger-ui.try-it-out-enabled` - Enable interactive testing
- `springdoc.swagger-ui.operations-sorter` - Sort operations by HTTP method
- `springdoc.swagger-ui.tags-sorter` - Sort tags alphabetically
- `springdoc.swagger-ui.display-request-duration` - Show request timing
- `springdoc.swagger-ui.doc-expansion` - Don't expand operations by default
- `springdoc.show-actuator` - Hide actuator endpoints from Swagger UI

## Customization

### Adding More Examples

To add more examples to the Swagger UI, edit `InitialController.java`:

```java
@ExampleObject(
    name = "Your Example Name",
    summary = "Brief description",
    value = """
        {
          "templateEncoded": "...",
          "docPropertiesJsonData": {...}
        }
        """
)
```

### Updating API Description

To update the main API description, edit `OpenApiConfig.java`:

```java
.description("""
    Your updated description here...
    """)
```

### Changing Server URLs

Update server URLs in `OpenApiConfig.java`:

```java
.servers(List.of(
    new Server()
        .url("http://your-server:port")
        .description("Your server description")
))
```

## Troubleshooting

### Swagger UI Not Loading

1. Check if the application is running: `curl http://localhost:8080/actuator/health`
2. Verify Swagger dependency in `pom.xml`
3. Run `mvn clean install` to rebuild
4. Check application logs for errors

### Examples Not Showing

1. Ensure `@ExampleObject` annotations are present in `InitialController.java`
2. Verify JSON syntax in example values
3. Restart the application

### PDF Download Not Working

1. Use a REST client like Postman or curl for testing
2. Swagger UI may not display PDF binary correctly
3. Check response headers: `Content-Type: application/pdf`

## Integration with CI/CD

### Generating OpenAPI Spec

Export the OpenAPI specification:

```bash
curl http://localhost:8080/v3/api-docs > openapi.json
```

### API Documentation Hosting

1. Export OpenAPI JSON
2. Host on platforms like:
   - SwaggerHub
   - Readme.io
   - ReDoc
   - Stoplight

## Security Considerations

### Production Deployment

For production environments:

1. **Disable Swagger UI:**
   ```properties
   springdoc.swagger-ui.enabled=false
   ```

2. **Protect with Authentication:**
   - Add Spring Security
   - Restrict access to `/swagger-ui.html` and `/v3/api-docs`

3. **Use HTTPS:**
   - Update server URLs in `OpenApiConfig.java` to use `https://`

## Additional Resources

- [SpringDoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Swagger UI Documentation](https://swagger.io/tools/swagger-ui/)
- [FlexMark README](./src/main/resources/flexmark_readme.md)

## Support

For questions or issues:
- Email: support@flexmark.example.com
- GitHub: https://github.com/yourorg/flexMarkProject/issues
