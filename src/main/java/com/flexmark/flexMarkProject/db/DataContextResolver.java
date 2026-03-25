package com.flexmark.flexMarkProject.db;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a Handlebars data context map for a named document type by executing
 * the SQL queries registered in {@link DocumentTypeRegistry}.
 *
 * <p>H2 is always auto-configured (spring-boot-starter-jdbc + H2 on the classpath),
 * so this bean is always available. The {@code poc} Spring profile enables H2 in
 * PostgreSQL compatibility mode and activates the H2 web console.
 */
@Service
public class DataContextResolver {

    private final DocumentTypeRegistry registry;
    private final NamedParameterJdbcTemplate jdbc;

    public DataContextResolver(DocumentTypeRegistry registry, NamedParameterJdbcTemplate jdbc) {
        this.registry = registry;
        this.jdbc = jdbc;
    }

    /**
     * Executes all queries for {@code documentType}, merging {@code runtimeParams} over each
     * query's static parameters, and returns a flat {@code Map<String, Object>} suitable for
     * use as the Handlebars context.
     *
     * @param documentType  a key registered in {@link DocumentTypeRegistry}
     * @param runtimeParams caller-supplied parameters that override static query params
     * @return assembled context map
     * @throws IllegalArgumentException if the document type is not registered
     */
    public Map<String, Object> resolve(String documentType, Map<String, Object> runtimeParams) {
        List<QueryDefinition> queries = registry.find(documentType)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown document type: '" + documentType + "'"));

        Map<String, Object> context = new HashMap<>();

        for (QueryDefinition query : queries) {
            Map<String, Object> params = mergeParams(query.getStaticParams(), runtimeParams);
            Object result = execute(query, params);
            context.put(query.getContextKey(), result);
        }

        return context;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> mergeParams(Map<String, Object> staticParams,
                                             Map<String, Object> runtimeParams) {
        Map<String, Object> merged = new HashMap<>(staticParams);
        merged.putAll(runtimeParams);   // runtime wins
        return merged;
    }

    private Object execute(QueryDefinition query, Map<String, Object> params) {
        return switch (query.getResultType()) {
            case SINGLE_ROW -> jdbc.queryForMap(query.getSql(), params);
            case LIST       -> jdbc.queryForList(query.getSql(), params);
            case SCALAR     -> {
                List<Map<String, Object>> rows = jdbc.queryForList(query.getSql(), params);
                yield rows.isEmpty() ? null : rows.get(0).values().iterator().next();
            }
        };
    }
}
