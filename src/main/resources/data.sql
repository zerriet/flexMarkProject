-- Seed data for FlexMark POC, derived from docs/datamap/business_loan_sample_data.json
-- loan_agreement id=1 → "business_loan_report" document type

INSERT INTO loan_agreements (id, agreement_ref, issue_date, currency, principal_formatted,
    interest_rate, tenure_months, monthly_instalment_fmt, purpose, loan_type,
    disbursement_date, first_repayment_date, maturity_date, processing_fee_fmt,
    debit_account_number, prepayment_fee_rate, prepayment_lock_months,
    total_principal_fmt, total_interest_fmt, total_payable_fmt)
VALUES (1, 'BLA-2026-03-00847', '24 March 2026', 'SGD', '500,000.00',
    '4.75', '12', '42,837.15', 'Working Capital & Equipment Procurement', 'Fixed Rate Term Loan',
    '1 April 2026', '1 May 2026', '1 April 2027', '2,500.00',
    '601-XXXXXX-001', '1.50', '6',
    '500,000.00', '14,045.80', '514,045.80');

INSERT INTO borrowers (id, loan_agreement_id, company_name, registration_number,
    registered_address, business_type, authorised_signatory, signatory_designation,
    email, contact_number)
VALUES (1, 1, 'Meridian Tech Solutions Pte. Ltd.', '202212345K',
    '18 Cross Street, #08-04 China Square Central, Singapore 048423',
    'Private Limited Company', 'Tan Wei Liang', 'Director',
    'weilian.tan@meridiantech.com.sg', '+65 6221 8800');

INSERT INTO repayment_schedule (id, loan_agreement_id, installment_no, due_date, opening_balance, principal, interest, instalment, closing_balance) VALUES
( 1, 1,  '1',  '1 May 2026',  '500,000.00', '40,858.98', '1,978.17', '42,837.15', '459,141.02'),
( 2, 1,  '2',  '1 Jun 2026',  '459,141.02', '41,020.44', '1,816.71', '42,837.15', '418,120.58'),
( 3, 1,  '3',  '1 Jul 2026',  '418,120.58', '41,182.53', '1,654.62', '42,837.15', '376,938.05'),
( 4, 1,  '4',  '1 Aug 2026',  '376,938.05', '41,345.27', '1,491.88', '42,837.15', '335,592.78'),
( 5, 1,  '5',  '1 Sep 2026',  '335,592.78', '41,508.65', '1,328.50', '42,837.15', '294,084.13'),
( 6, 1,  '6',  '1 Oct 2026',  '294,084.13', '41,672.68', '1,164.47', '42,837.15', '252,411.45'),
( 7, 1,  '7',  '1 Nov 2026',  '252,411.45', '41,837.36',   '999.79', '42,837.15', '210,574.09'),
( 8, 1,  '8',  '1 Dec 2026',  '210,574.09', '42,002.70',   '834.45', '42,837.15', '168,571.39'),
( 9, 1,  '9',  '1 Jan 2027',  '168,571.39', '42,168.69',   '668.46', '42,837.15', '126,402.70'),
(10, 1, '10',  '1 Feb 2027',  '126,402.70', '42,335.34',   '501.81', '42,837.15',  '84,067.36'),
(11, 1, '11',  '1 Mar 2027',   '84,067.36', '42,502.65',   '334.50', '42,837.15',  '41,564.71'),
(12, 1, '12',  '1 Apr 2027',   '41,564.71', '41,564.71',   '164.62', '41,729.33',       '0.00');

INSERT INTO loan_security (id, loan_agreement_id, security_type, description)
VALUES (1, 1, 'Personal Guarantee',
    'Unlimited personal guarantee provided by Tan Wei Liang (NRIC: SXXXXXXXA), Director of the Borrower');

INSERT INTO bank_officers (id, loan_agreement_id, bank_name, officer_name, officer_title)
VALUES (1, 1, 'Demo Bank Ltd.', 'Sarah Lim Mei Ling', 'Senior Relationship Manager, Business Banking');
