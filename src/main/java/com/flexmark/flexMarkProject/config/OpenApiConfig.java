package com.flexmark.flexMarkProject.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * <p>
 * Configures Swagger UI with comprehensive API metadata, contact information,
 * and server details for the FlexMark PDF generation service.
 * </p>
 * <p>
 * Access the Swagger UI at: <strong>http://localhost:8080/swagger-ui.html</strong>
 * </p>
 * <p>
 * Access the OpenAPI JSON at: <strong>http://localhost:8080/v3/api-docs</strong>
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flexMarkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlexMark PDF Generator API")
                        .description("""
                                # FlexMark PDF Document Generation Service

                                A specialized microservice for generating high-fidelity PDF documents using a template-first hybrid approach.

                                ## Key Features

                                ### 📝 Template-First Architecture
                                - **Handlebars Templating**: Dynamic content with variables, loops (`{{#each}}`), and conditionals (`{{#if}}`)
                                - **Markdown Support**: Wrap content in `<md>` tags for automatic Markdown-to-HTML conversion
                                - **Modern CSS**: Full support for Flexbox, Grid layouts, and modern CSS features

                                ### 🎨 Styling & Layout
                                - Custom CSS injection for complete design control
                                - Headers and footers with data URI image support
                                - XHTML compliance automatically enforced for PDF rendering

                                ### 🖼️ Image Handling
                                - **Data URI Support**: Embed images as Base64 without temporary files
                                - **On-the-fly Decoding**: Images decoded during PDF rendering
                                - **Zero File Overhead**: No temp directory clutter
                                - **Supported Formats**: PNG, JPEG, GIF, WebP, SVG

                                ### 🔒 Security
                                - **SSRF Protection**: External HTTP/HTTPS requests blocked
                                - **Data URI Validation**: RFC 2397 compliant parsing
                                - **Input Validation**: Jakarta Bean Validation at controller layer
                                - **Secure Resource Retrieval**: Only local and data URI resources allowed

                                ## Pipeline Overview

                                The service follows a strict 6-stage pipeline:

                                1. **Input Validation** - Validate request structure and required fields
                                2. **Input Decoding** - Decode Base64-encoded resources
                                3. **Handlebars Templating** - Merge dynamic data into template
                                4. **DOM Processing** - Render Markdown blocks to HTML
                                5. **DOM Assembly** - Inject CSS, headers, footers; enforce XHTML
                                6. **PDF Rendering** - Convert to PDF with iText7

                                ## Example Use Cases

                                - **Reports**: Sales reports, analytics dashboards, financial statements
                                - **Invoices**: Dynamic invoices with company branding
                                - **Certificates**: Personalized certificates with embedded signatures
                                - **Documentation**: Technical docs with code blocks and tables
                                - **Letters**: Branded business correspondence

                                ## Technology Stack

                                - **Spring Boot 4.0** - REST API framework
                                - **Handlebars 4.3** - Template engine
                                - **Flexmark 0.64** - Markdown processing
                                - **Jsoup** - HTML parsing and XHTML enforcement
                                - **iText7 8.0** - PDF generation
                                - **Jakarta Validation** - Input validation

                                ## Getting Started

                                1. Prepare your HTML template with Handlebars syntax
                                2. Encode template, CSS, headers, footers to Base64
                                3. Send POST request to `/api/content/submit`
                                4. Receive PDF binary in response

                                For detailed examples, see the endpoint documentation below.
                                """)
                        .version("6.0.0")
                        .contact(new Contact()
                                .name("FlexMark Team")
                                .email("support@flexmark.example.com")
                                .url("https://github.com/yourorg/flexMarkProject")
                        )
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")
                        )
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api.flexmark.example.com")
                                .description("Production server")
                ));
    }
}
