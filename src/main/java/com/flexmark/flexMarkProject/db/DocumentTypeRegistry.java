package com.flexmark.flexMarkProject.db;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of known document types and the SQL queries used to resolve their data context.
 *
 * <p>In this POC the mappings are hardcoded. A future phase can persist them to a database
 * table and expose an admin API for management.
 *
 * <p>Document type: {@code "business_loan_report"}
 * <ul>
 *   <li>Resolves {@code agreementRef} and {@code issueDate} from the loan agreement</li>
 *   <li>Resolves {@code borrower} (single row) from the borrowers table</li>
 *   <li>Resolves {@code loan} (single row) from the loan_agreements table</li>
 *   <li>Resolves {@code schedule} (list) from the repayment_schedule table</li>
 *   <li>Resolves {@code bank} (single row) from the bank_officers table</li>
 * </ul>
 */
@Service
public class DocumentTypeRegistry {

    private static final String AGREEMENT_ID_PARAM = "agreementId";

    private final Map<String, List<QueryDefinition>> registry;

    public DocumentTypeRegistry() {
        this.registry = Map.of(
            "business_loan_report", buildBusinessLoanQueries()
        );
    }

    /**
     * Returns the list of {@link QueryDefinition}s for the given document type,
     * or {@link Optional#empty()} if the type is not registered.
     */
    public Optional<List<QueryDefinition>> find(String documentType) {
        return Optional.ofNullable(registry.get(documentType));
    }

    // -------------------------------------------------------------------------
    // Query definitions
    // -------------------------------------------------------------------------

    private List<QueryDefinition> buildBusinessLoanQueries() {
        return List.of(

            // Top-level agreement fields (agreementRef, issueDate)
            new QueryDefinition(
                "agreementRef",
                "SELECT agreement_ref FROM loan_agreements WHERE id = :agreementId",
                QueryDefinition.ResultType.SCALAR,
                Map.of(AGREEMENT_ID_PARAM, 1)
            ),

            new QueryDefinition(
                "issueDate",
                "SELECT issue_date FROM loan_agreements WHERE id = :agreementId",
                QueryDefinition.ResultType.SCALAR,
                Map.of(AGREEMENT_ID_PARAM, 1)
            ),

            // borrower object → {{borrower.companyName}}, {{borrower.reg}}, {{borrower.address}},
            //                   {{borrower.type}}, {{borrower.signatory}}, {{borrower.designation}}
            new QueryDefinition(
                "borrower",
                """
                SELECT company_name          AS "companyName",
                       registration_number   AS "reg",
                       registered_address    AS "address",
                       business_type         AS "type",
                       authorised_signatory  AS "signatory",
                       signatory_designation AS "designation",
                       email,
                       contact_number        AS "contactNumber"
                FROM borrowers
                WHERE loan_agreement_id = :agreementId
                """,
                QueryDefinition.ResultType.SINGLE_ROW,
                Map.of(AGREEMENT_ID_PARAM, 1)
            ),

            // loan object → {{loan.currency}}, {{loan.principal}}, {{loan.interestRate}},
            //               {{loan.tenure}}, {{loan.instalment}}, {{loan.purpose}},
            //               {{loan.disbursementDate}}, {{loan.firstRepayment}}, {{loan.maturityDate}}
            new QueryDefinition(
                "loan",
                """
                SELECT currency,
                       principal_formatted    AS "principal",
                       interest_rate          AS "interestRate",
                       tenure_months          AS "tenure",
                       monthly_instalment_fmt AS "instalment",
                       purpose,
                       loan_type              AS "type",
                       disbursement_date      AS "disbursementDate",
                       first_repayment_date   AS "firstRepayment",
                       maturity_date          AS "maturityDate",
                       processing_fee_fmt     AS "processingFeeFormatted",
                       debit_account_number   AS "debitAccountNumber",
                       prepayment_fee_rate    AS "prepaymentFeeRate",
                       prepayment_lock_months AS "prepaymentLockInMonths",
                       total_principal_fmt    AS "totalPrincipalFormatted",
                       total_interest_fmt     AS "totalInterestFormatted",
                       total_payable_fmt      AS "totalPayableFormatted"
                FROM loan_agreements
                WHERE id = :agreementId
                """,
                QueryDefinition.ResultType.SINGLE_ROW,
                Map.of(AGREEMENT_ID_PARAM, 1)
            ),

            // schedule list → {{#each schedule}} {{installmentNo}} {{dueDate}} … {{/each}}
            new QueryDefinition(
                "schedule",
                """
                SELECT installment_no    AS "installmentNo",
                       due_date          AS "dueDate",
                       opening_balance   AS "openingBalance",
                       principal,
                       interest,
                       instalment,
                       closing_balance   AS "closingBalance"
                FROM repayment_schedule
                WHERE loan_agreement_id = :agreementId
                ORDER BY CAST(installment_no AS INTEGER)
                """,
                QueryDefinition.ResultType.LIST,
                Map.of(AGREEMENT_ID_PARAM, 1)
            ),

            // bank object → {{bank.name}}, {{bank.officer}}, {{bank.officerTitle}}
            new QueryDefinition(
                "bank",
                """
                SELECT bank_name    AS "name",
                       officer_name AS "officer",
                       officer_title AS "officerTitle"
                FROM bank_officers
                WHERE loan_agreement_id = :agreementId
                """,
                QueryDefinition.ResultType.SINGLE_ROW,
                Map.of(AGREEMENT_ID_PARAM, 1)
            )
        );
    }
}
