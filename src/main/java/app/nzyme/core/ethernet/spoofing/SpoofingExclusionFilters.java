package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class SpoofingExclusionFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "description", operator), "");
            case "enabled":
                return GeneratedSql.create(booleanMatch(bindId, "enabled", operator), "");
            case "type":
                return GeneratedSql.create(typeMatch(bindId, operator), "");
            case "address_cidr":
                return GeneratedSql.create(stringMatch(bindId, "address_cidr", operator), "");
            case "mac":
                return GeneratedSql.create(stringMatch(bindId, "mac", operator), "");
            case "match_count":
                return GeneratedSql.create(numericMatch(bindId, "match_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    /*
     * Types are stored as a comma-separated list. An exclusion without types matches every type, so it is never
     * "equal" to a type and always "not equal".
     */
    private static String typeMatch(String bindId, FilterOperator operator) {
        String contains = "(',' || types || ',') LIKE ('%,' || :" + bindId + " || ',%')";
        switch (operator) {
            case EQUALS:
                return "(types IS NOT NULL AND " + contains + ")";
            case NOT_EQUALS:
                return "(types IS NULL OR NOT " + contains + ")";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for type field.");
        }
    }

}
