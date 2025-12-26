# Test Resources

This directory contains test resources for the flexMarkProject test suite.

## Structure

- `templates/` - Sample HTML/Markdown templates for testing
- `payloads/` - Sample JSON request payloads for integration tests

## Templates

### simple-template.html
Basic HTML template with Handlebars variables for testing template substitution.

### markdown-template.html
Template with Markdown content inside `<md>` tags for testing Markdown conversion.

## Payloads

### test-payload-simple.json
Minimal valid request payload with just a template (Base64: "<div><h1>Test Document</h1></div>").

## Usage

These resources can be loaded in tests using Spring's `@Value` annotation or `ResourceLoader`:

```java
@Value("classpath:templates/simple-template.html")
private Resource templateResource;
```
