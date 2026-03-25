package com.flexmark.flexMarkProject.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTypeRegistryTest {

    private DocumentTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DocumentTypeRegistry();
    }

    @Test
    void knownType_returnsNonEmptyList() {
        Optional<List<QueryDefinition>> result = registry.find("business_loan_report");
        assertTrue(result.isPresent());
        assertFalse(result.get().isEmpty());
    }

    @Test
    void unknownType_returnsEmpty() {
        Optional<List<QueryDefinition>> result = registry.find("nonexistent_type");
        assertTrue(result.isEmpty());
    }

    @Test
    void allQueryDefinitions_haveRequiredFields() {
        List<QueryDefinition> queries = registry.find("business_loan_report").orElseThrow();
        for (QueryDefinition q : queries) {
            assertNotNull(q.getContextKey(),  "contextKey must not be null");
            assertFalse(q.getContextKey().isBlank(), "contextKey must not be blank");
            assertNotNull(q.getSql(),         "sql must not be null");
            assertFalse(q.getSql().isBlank(), "sql must not be blank");
            assertNotNull(q.getResultType(),  "resultType must not be null");
            assertNotNull(q.getStaticParams(), "staticParams must not be null");
        }
    }

    @Test
    void businessLoanReport_hasExpectedContextKeys() {
        List<QueryDefinition> queries = registry.find("business_loan_report").orElseThrow();
        List<String> keys = queries.stream().map(QueryDefinition::getContextKey).toList();
        assertTrue(keys.contains("agreementRef"), "expected agreementRef key");
        assertTrue(keys.contains("issueDate"),    "expected issueDate key");
        assertTrue(keys.contains("borrower"),     "expected borrower key");
        assertTrue(keys.contains("loan"),         "expected loan key");
        assertTrue(keys.contains("schedule"),     "expected schedule key");
        assertTrue(keys.contains("bank"),         "expected bank key");
    }

    @Test
    void scheduleQuery_isListResultType() {
        List<QueryDefinition> queries = registry.find("business_loan_report").orElseThrow();
        QueryDefinition scheduleQuery = queries.stream()
            .filter(q -> "schedule".equals(q.getContextKey()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("schedule query not found"));
        assertEquals(QueryDefinition.ResultType.LIST, scheduleQuery.getResultType());
    }
}
