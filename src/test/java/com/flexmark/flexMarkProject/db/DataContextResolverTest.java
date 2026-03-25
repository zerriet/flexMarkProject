package com.flexmark.flexMarkProject.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("poc")
class DataContextResolverTest {

    @Autowired
    private DataContextResolver resolver;

    @Test
    void resolve_businessLoanReport_returnsPopulatedContext() {
        Map<String, Object> context = resolver.resolve("business_loan_report", Map.of());

        assertNotNull(context.get("agreementRef"), "agreementRef should be present");
        assertNotNull(context.get("issueDate"),    "issueDate should be present");
        assertNotNull(context.get("borrower"),     "borrower should be present");
        assertNotNull(context.get("loan"),         "loan should be present");
        assertNotNull(context.get("schedule"),     "schedule should be present");
        assertNotNull(context.get("bank"),         "bank should be present");
    }

    @Test
    void resolve_agreementRef_isCorrectValue() {
        Map<String, Object> context = resolver.resolve("business_loan_report", Map.of());
        assertEquals("BLA-2026-03-00847", context.get("agreementRef"));
    }

    @Test
    void resolve_borrower_hasCamelCaseKeys() {
        Map<String, Object> context = resolver.resolve("business_loan_report", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> borrower = (Map<String, Object>) context.get("borrower");
        assertNotNull(borrower.get("companyName"),         "companyName should be aliased");
        assertNotNull(borrower.get("registrationNumber"),  "registrationNumber should be aliased");
        assertNotNull(borrower.get("authorisedSignatory"), "authorisedSignatory should be aliased");
    }

    @Test
    void resolve_schedule_is12Rows() {
        Map<String, Object> context = resolver.resolve("business_loan_report", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) context.get("schedule");
        assertEquals(12, schedule.size(), "repayment schedule should have 12 rows");
    }

    @Test
    void resolve_schedule_firstRowHasExpectedKeys() {
        Map<String, Object> context = resolver.resolve("business_loan_report", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) context.get("schedule");
        Map<String, Object> firstRow = schedule.get(0);
        assertTrue(firstRow.containsKey("installmentNo"),  "installmentNo key expected");
        assertTrue(firstRow.containsKey("dueDate"),        "dueDate key expected");
        assertTrue(firstRow.containsKey("openingBalance"), "openingBalance key expected");
        assertTrue(firstRow.containsKey("closingBalance"), "closingBalance key expected");
    }

    @Test
    void resolve_runtimeParams_overrideStaticParams() {
        // Runtime param agreementId=999 should cause "no row found" for single-row queries.
        // We verify this throws rather than silently returning empty/null.
        assertThrows(Exception.class,
            () -> resolver.resolve("business_loan_report", Map.of("agreementId", 999)),
            "Querying with a non-existent agreementId should throw");
    }

    @Test
    void resolve_unknownDocumentType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve("unknown_type", Map.of()));
    }
}
