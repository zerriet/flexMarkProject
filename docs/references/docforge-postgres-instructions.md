# docforge — PostgreSQL Migration Instructions for Claude Code

## Context

This is the `docforge` project (Java Hybrid Document Generator), a Spring Boot microservice that generates PDFs from Handlebars + Markdown templates using iText7. The project currently uses an **H2 in-memory database** (via `application-poc.properties`) for a POC feature (Feature 2: Database-Driven Data Injection). The goal is to migrate this to **PostgreSQL running locally on macOS** for proper local testing.

---

## Current H2 Setup (Reference)

**Profile:** `poc` (`application-poc.properties`)  
**Mode:** H2 in PostgreSQL compatibility mode (`MODE=PostgreSQL`)  
**Console:** H2 web console at `/h2-console`  
**Schema file:** `src/main/resources/schema.sql`  
**Seed data file:** `src/main/resources/data.sql`

**Existing tables (5):**
- `loan_agreements`
- `borrowers`
- `repayment_schedule`
- `loan_security`
- `bank_officers`

**Registered document type:** `business_loan_report`  
**Query keys:** `agreementRef`, `issueDate`, `borrower`, `loan`, `schedule`, `bank`

---

## Target PostgreSQL Setup

**Database name:** `docforge`  
**Host:** `localhost`  
**Port:** `5432`  
**Username:** `mark` (local macOS user, no password)  
**Driver:** `org.postgresql.Driver`

---

## Migration Tasks

### Task 1 — Add PostgreSQL dependency to `pom.xml`

Add the PostgreSQL driver alongside the existing H2 dependency:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Do NOT remove the H2 dependency — keep it for unit tests that may rely on it.

---

### Task 2 — Create a new Spring profile for local PostgreSQL

Create `src/main/resources/application-local.properties`:

```properties
# Local PostgreSQL profile for docforge
spring.datasource.url=jdbc:postgresql://localhost:5432/docforge
spring.datasource.username=mark
spring.datasource.password=
spring.datasource.driver-class-name=org.postgresql.Driver

# Run schema.sql and data.sql on startup
spring.sql.init.mode=always
spring.sql.init.platform=postgresql

# Disable H2 console
spring.h2.console.enabled=false

# Connection pool
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.connection-timeout=30000
```

Do NOT modify `application-poc.properties` — preserve the H2 POC profile as-is.

---

### Task 3 — Make `schema.sql` PostgreSQL-compatible

The existing `schema.sql` was written for H2 in PostgreSQL compatibility mode. Review it and ensure the following:

- Replace `AUTO_INCREMENT` with `SERIAL` or `BIGSERIAL` if present
- Replace H2-specific types with standard PostgreSQL types
- Add `IF NOT EXISTS` to all `CREATE TABLE` statements to make reruns safe
- Ensure foreign key references are correct

**Expected final schema:**

```sql
CREATE TABLE IF NOT EXISTS borrowers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    registration_number VARCHAR(100),
    address TEXT,
    contact_person VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS loan_agreements (
    id SERIAL PRIMARY KEY,
    agreement_ref VARCHAR(100) UNIQUE NOT NULL,
    issue_date DATE,
    loan_amount NUMERIC(15,2),
    interest_rate NUMERIC(5,2),
    tenure_months INT,
    purpose TEXT,
    borrower_id INT REFERENCES borrowers(id)
);

CREATE TABLE IF NOT EXISTS repayment_schedule (
    id SERIAL PRIMARY KEY,
    agreement_ref VARCHAR(100) REFERENCES loan_agreements(agreement_ref),
    instalment_no INT,
    due_date DATE,
    principal NUMERIC(15,2),
    interest NUMERIC(15,2),
    total_payment NUMERIC(15,2),
    balance NUMERIC(15,2)
);

CREATE TABLE IF NOT EXISTS loan_security (
    id SERIAL PRIMARY KEY,
    agreement_ref VARCHAR(100) REFERENCES loan_agreements(agreement_ref),
    security_type VARCHAR(100),
    description TEXT,
    estimated_value NUMERIC(15,2)
);

CREATE TABLE IF NOT EXISTS bank_officers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    designation VARCHAR(255),
    department VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50)
);
```

---

### Task 4 — Verify `data.sql` compatibility

Review `data.sql` (seed data for `business_loan_report`) and ensure:

- No H2-specific syntax (e.g., no `MERGE INTO`, no `SET IDENTITY INSERT`)
- Use standard PostgreSQL `INSERT INTO ... ON CONFLICT DO NOTHING` if idempotency is needed
- Dates are in `YYYY-MM-DD` format
- Numeric values have no currency symbols

---

### Task 5 — Run and verify

**Start the app with the local profile:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Verify via DBeaver (already configured and connected to docforge on localhost:5432):**
1. Refresh the `docforge` connection
2. Navigate to Schemas → public → Tables
3. Confirm all 5 tables exist with seed data

**Verify via API:**
```bash
curl -X POST http://localhost:8080/api/content/submit \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "business_loan_report",
    "templateEncoded": "<YOUR_BASE64_TEMPLATE>"
  }'
```

---

## What NOT to change

- Do NOT modify `MarkdownService` — it is database-agnostic
- Do NOT modify `DocumentTypeRegistry` or `DataContextResolver` — they use `NamedParameterJdbcTemplate` which works with any JDBC datasource
- Do NOT remove the H2 dependency or `application-poc.properties`
- Do NOT change the controller logic — only the datasource config changes

---

## File Change Summary

| File | Action |
|------|--------|
| `pom.xml` | Add PostgreSQL driver dependency |
| `src/main/resources/application-local.properties` | Create new (PostgreSQL local profile) |
| `src/main/resources/schema.sql` | Update for PostgreSQL compatibility |
| `src/main/resources/data.sql` | Verify/update for PostgreSQL compatibility |

---

## Notes

- The `docforge` database must already exist before running the app. Create it with: `createdb docforge`
- `spring.sql.init.mode=always` will re-run `schema.sql` and `data.sql` on every startup — `IF NOT EXISTS` in the DDL prevents duplicate table creation errors
- DBeaver is already set up and connected to `docforge` on `localhost:5432` for visual inspection
