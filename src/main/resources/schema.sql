-- Schema for FlexMark POC: business loan document data
-- Designed in PostgreSQL-compatible SQL (H2 PostgreSQL mode)

CREATE TABLE IF NOT EXISTS loan_agreements (
    id                      BIGINT PRIMARY KEY,
    agreement_ref           VARCHAR(50)  NOT NULL,
    issue_date              VARCHAR(50)  NOT NULL,
    currency                VARCHAR(10)  NOT NULL,
    principal_formatted     VARCHAR(30)  NOT NULL,
    interest_rate           VARCHAR(10)  NOT NULL,
    tenure_months           VARCHAR(10)  NOT NULL,
    monthly_instalment_fmt  VARCHAR(30)  NOT NULL,
    purpose                 VARCHAR(200) NOT NULL,
    loan_type               VARCHAR(100) NOT NULL,
    disbursement_date       VARCHAR(50)  NOT NULL,
    first_repayment_date    VARCHAR(50)  NOT NULL,
    maturity_date           VARCHAR(50)  NOT NULL,
    processing_fee_fmt      VARCHAR(30)  NOT NULL,
    debit_account_number    VARCHAR(50)  NOT NULL,
    prepayment_fee_rate     VARCHAR(10)  NOT NULL,
    prepayment_lock_months  VARCHAR(10)  NOT NULL,
    total_principal_fmt     VARCHAR(30)  NOT NULL,
    total_interest_fmt      VARCHAR(30)  NOT NULL,
    total_payable_fmt       VARCHAR(30)  NOT NULL
);

CREATE TABLE IF NOT EXISTS borrowers (
    id                      BIGINT PRIMARY KEY,
    loan_agreement_id       BIGINT       NOT NULL REFERENCES loan_agreements(id),
    company_name            VARCHAR(200) NOT NULL,
    registration_number     VARCHAR(50)  NOT NULL,
    registered_address      VARCHAR(500) NOT NULL,
    business_type           VARCHAR(100) NOT NULL,
    authorised_signatory    VARCHAR(100) NOT NULL,
    signatory_designation   VARCHAR(100) NOT NULL,
    email                   VARCHAR(200) NOT NULL,
    contact_number          VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS repayment_schedule (
    id                      BIGINT PRIMARY KEY,
    loan_agreement_id       BIGINT      NOT NULL REFERENCES loan_agreements(id),
    installment_no          VARCHAR(5)  NOT NULL,
    due_date                VARCHAR(30) NOT NULL,
    opening_balance         VARCHAR(20) NOT NULL,
    principal               VARCHAR(20) NOT NULL,
    interest                VARCHAR(20) NOT NULL,
    instalment              VARCHAR(20) NOT NULL,
    closing_balance         VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS loan_security (
    id                      BIGINT PRIMARY KEY,
    loan_agreement_id       BIGINT       NOT NULL REFERENCES loan_agreements(id),
    security_type           VARCHAR(100) NOT NULL,
    description             VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS bank_officers (
    id                      BIGINT PRIMARY KEY,
    loan_agreement_id       BIGINT       NOT NULL REFERENCES loan_agreements(id),
    bank_name               VARCHAR(200) NOT NULL,
    officer_name            VARCHAR(100) NOT NULL,
    officer_title           VARCHAR(200) NOT NULL
);
