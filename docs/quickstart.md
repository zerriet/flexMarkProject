# QuickStart Guide

Testing the two new features added in the 2026-03-25 release:
1. **Split-Pane Editor** — browser-based template authoring with live preview
2. **Database-Driven Data Injection** — resolve Handlebars data from H2 instead of sending JSON

---

## Prerequisites

- Java 21
- Maven wrapper (`mvnw`) in the project root

---

## 1. Start the Application

### Default mode (inline JSON data, editor available)

```bash
./mvnw spring-boot:run
```

- API: `http://localhost:8080/api/content/submit`
- Editor: `http://localhost:8080/`
- Swagger: `http://localhost:8080/swagger-ui.html`

### POC mode (adds H2 database + web console)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=poc
```

Additional endpoints:
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:flexmarkdb`
  - Username: `sa` / Password: *(blank)*

---

## 2. Test the Split-Pane Editor

1. Start the app (either mode)
2. Open `http://localhost:8080/` in a browser
3. The editor loads pre-filled with the business loan template and sample JSON data

**Live preview:**
- Edit the template in the left pane — the right pane updates within 300ms
- Modify the Sample Data JSON panel to see different values substituted into `{{variable}}` tokens
- `<md>` blocks are rendered as Markdown in the preview

**PDF export (inline mode):**
1. Leave the **Document type** field blank
2. Click **Generate PDF** (or press `Cmd+Enter` / `Ctrl+Enter`)
3. The PDF opens in a new tab — it reflects the template + sample data JSON

**PDF export (DB-driven mode — requires poc profile):**
1. Start the app with the `poc` profile
2. Type `business_loan_report` in the **Document type** toolbar field
3. Click **Generate PDF**
4. The server resolves data from H2 — the PDF matches `docs/samples/business_loan_completed_1.pdf`

---

## 3. Test the API Directly (Swagger)

Open `http://localhost:8080/swagger-ui.html`, expand **POST /api/content/submit**, click **Try it out**.

### 3a. Inline JSON mode

```json
{
  "templateEncoded": "PGgxPnt7Z3JlZXRpbmd9fTwvaDE+",
  "docPropertiesJsonData": {
    "greeting": "Hello from FlexMark!"
  }
}
```

> `PGgxPnt7Z3JlZXRpbmd9fTwvaDE+` is Base64 for `<h1>{{greeting}}</h1>`

Expected: `200 OK` with `Content-Type: application/pdf`

### 3b. DB-driven mode (poc profile required)

```json
{
  "templateEncoded": "<base64 of docs/templates/business_loan_template.html>",
  "documentType": "business_loan_report"
}
```

To get the Base64 of the template on macOS/Linux:
```bash
base64 -i docs/templates/business_loan_template.html | tr -d '\n'
```

Expected: `200 OK` — PDF with Meridian Tech Solutions loan agreement, 12-row schedule.

### 3c. DB-driven mode with runtime parameter override

```json
{
  "templateEncoded": "<base64 of template>",
  "documentType": "business_loan_report",
  "queryParams": {
    "agreementId": 1
  }
}
```

`queryParams` override the static defaults in `DocumentTypeRegistry`. Use this to select a different record when multiple loan agreements exist.

---

## 4. Run the Tests

### Full test suite (all 66 tests, default profile)

```bash
./mvnw test
```

### Only the new DB-layer tests

```bash
./mvnw test -Dtest="DocumentTypeRegistryTest,DataContextResolverTest,InitialControllerDbIntegrationTest"
```

### Verify the original tests are unaffected

```bash
./mvnw test -Dtest="InitialControllerIntegrationTest,MarkdownServiceTest,SecureDataUriResourceRetrieverTest"
```

Expected output for all runs:
```
Tests run: N, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 5. Inspect the H2 Database (poc profile)

1. Start with `poc` profile
2. Open `http://localhost:8080/h2-console`
3. Connect with JDBC URL `jdbc:h2:mem:flexmarkdb`, username `sa`, blank password

Useful queries:
```sql
-- Check seed data loaded
SELECT COUNT(*) FROM repayment_schedule;   -- expect 12

-- View borrower record
SELECT * FROM borrowers;

-- Preview the data as the resolver will return it
SELECT company_name AS "companyName",
       registration_number AS "registrationNumber",
       authorised_signatory AS "authorisedSignatory"
FROM borrowers WHERE loan_agreement_id = 1;
```

---

## 6. File Reference

| Path | Purpose |
|---|---|
| `docs/templates/business_loan_template.html` | Sample Handlebars + `<md>` template |
| `docs/datamap/business_loan_sample_data.json` | Sample data matching the template |
| `docs/samples/business_loan_completed_1.pdf` | Expected PDF output |
| `src/main/resources/schema.sql` | H2 table DDL |
| `src/main/resources/data.sql` | Seed data |
| `src/main/resources/application-poc.properties` | POC profile config |
| `src/main/java/.../db/DocumentTypeRegistry.java` | Add new document types here |
| `src/main/resources/static/index.html` | Editor entry point |
