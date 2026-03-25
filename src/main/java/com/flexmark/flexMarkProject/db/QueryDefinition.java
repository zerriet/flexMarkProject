package com.flexmark.flexMarkProject.db;

import java.util.Map;

/**
 * Defines a single database query that contributes one key to the Handlebars data context.
 *
 * <ul>
 *   <li>{@code SINGLE_ROW} – query returns one row; mapped to {@code Map<String,Object>}</li>
 *   <li>{@code LIST}       – query returns many rows; mapped to {@code List<Map<String,Object>>}</li>
 *   <li>{@code SCALAR}     – query returns a single cell; mapped to the raw value</li>
 * </ul>
 */
public class QueryDefinition {

    public enum ResultType { SINGLE_ROW, LIST, SCALAR }

    private final String contextKey;
    private final String sql;
    private final ResultType resultType;
    private final Map<String, Object> staticParams;

    public QueryDefinition(String contextKey, String sql, ResultType resultType, Map<String, Object> staticParams) {
        this.contextKey = contextKey;
        this.sql = sql;
        this.resultType = resultType;
        this.staticParams = staticParams;
    }

    public String getContextKey()              { return contextKey; }
    public String getSql()                     { return sql; }
    public ResultType getResultType()          { return resultType; }
    public Map<String, Object> getStaticParams() { return staticParams; }
}
