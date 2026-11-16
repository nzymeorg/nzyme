package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class FindingFilters implements SqlFilterProvider {

    private static final String TARGETS = "SELECT 1 FROM jsonb_array_elements(f.targets) AS elem WHERE ";
    private static final String TARGET_ADDRESS = "(elem ->> 'address')::inet";
    private static final String TARGET_PORT = "(elem ->> 'port')::int";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "policy_name":
                return GeneratedSql.create(stringMatch(bindId, "p.name", operator), "");
            case "finding_class":
                return GeneratedSql.create(stringMatch(bindId, "f.finding_class", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "f.source_address", operator), "");
            case "source_asn":
                return GeneratedSql.create(numericMatch(bindId, "f.source_asn", operator), "");
            case "source_country_code":
                return GeneratedSql.create(stringMatch(bindId, "f.source_country_code", operator), "");
            case "target_address":
                return GeneratedSql.create(targetAddressMatch(bindId, operator), "");
            case "target_port":
                return GeneratedSql.create(targetPortMatch(bindId, operator), "");
            case "audit":
                return GeneratedSql.create(booleanMatch(bindId, "f.audit", operator), "");
            case "session_count":
                return GeneratedSql.create(numericMatch(bindId, "f.session_count", operator), "");
            case "target_count":
                return GeneratedSql.create(numericMatch(bindId, "f.target_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    private static String targetAddressMatch(String bindId, FilterOperator operator) {
        switch (operator) {
            case NOT_EQUALS:
                return none(ipAddressMatch(bindId, TARGET_ADDRESS, FilterOperator.EQUALS));
            case NOT_REGEX_MATCH:
                return none(ipAddressMatch(bindId, TARGET_ADDRESS, FilterOperator.REGEX_MATCH));
            case NOT_IN_CIDR:
                return none(ipAddressMatch(bindId, TARGET_ADDRESS, FilterOperator.IN_CIDR));
            case IS_NOT_PRIVATE:
                return none(ipAddressMatch(bindId, TARGET_ADDRESS, FilterOperator.IS_PRIVATE));
            case IS_NOT_CGNAT:
                return none(ipAddressMatch(bindId, TARGET_ADDRESS, FilterOperator.IS_CGNAT));
            case IS_NULL:
                return "NOT EXISTS (" + TARGETS + "TRUE)";
            case IS_NOT_NULL:
                return "EXISTS (" + TARGETS + "TRUE)";
            default:
                return any(ipAddressMatch(bindId, TARGET_ADDRESS, operator));
        }
    }

    private static String targetPortMatch(String bindId, FilterOperator operator) {
        switch (operator) {
            case NOT_EQUALS_NUMERIC:
                return none(numericMatch(bindId, TARGET_PORT, FilterOperator.EQUALS_NUMERIC));
            case IS_NULL:
                return "NOT EXISTS (" + TARGETS + "TRUE)";
            case IS_NOT_NULL:
                return "EXISTS (" + TARGETS + "TRUE)";
            default:
                return any(numericMatch(bindId, TARGET_PORT, operator));
        }
    }

    private static String any(String condition) {
        return "EXISTS (" + TARGETS + condition + ")";
    }

    private static String none(String condition) {
        return "NOT EXISTS (" + TARGETS + condition + ")";
    }

}
