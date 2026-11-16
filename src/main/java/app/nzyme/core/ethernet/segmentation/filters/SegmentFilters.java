package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class SegmentFilters implements SqlFilterProvider {

    private static final String MEMBERS = "SELECT 1 FROM segmentation_segment_members AS m WHERE m.segment_id = s.id AND ";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "s.name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "s.description", operator), "");
            case "kind":
                return GeneratedSql.create(stringMatch(bindId, "s.kind", operator), "");
            case "network":
                return GeneratedSql.create(networkMatch(bindId, operator), "");
            case "asn":
                return GeneratedSql.create(asnMatch(bindId, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    // An address equals a segment that one of its networks contains, and a CIDR takes in a segment that has a network
    // inside of it. The negations hold if no network of the segment does.
    private static String networkMatch(String bindId, FilterOperator operator) {
        switch (operator) {
            case EQUALS:
                return "EXISTS (" + MEMBERS + "m.network >>= :" + bindId + "::inet)";
            case NOT_EQUALS:
                return "NOT EXISTS (" + MEMBERS + "m.network >>= :" + bindId + "::inet)";
            case IN_CIDR:
                return "EXISTS (" + MEMBERS + "m.network <<= :" + bindId + "::cidr)";
            case NOT_IN_CIDR:
                return "NOT EXISTS (" + MEMBERS + "m.network <<= :" + bindId + "::cidr)";
            case REGEX_MATCH:
                return "EXISTS (" + MEMBERS + "m.network::text ~ :" + bindId + ")";
            case NOT_REGEX_MATCH:
                return "NOT EXISTS (" + MEMBERS + "m.network::text ~ :" + bindId + ")";
            case IS_PRIVATE:
                return "EXISTS (" + MEMBERS + ipAddressMatch(bindId, "m.network", FilterOperator.IS_PRIVATE) + ")";
            case IS_NOT_PRIVATE:
                return "NOT EXISTS (" + MEMBERS + ipAddressMatch(bindId, "m.network", FilterOperator.IS_PRIVATE) + ")";
            case IS_CGNAT:
                return "EXISTS (" + MEMBERS + ipAddressMatch(bindId, "m.network", FilterOperator.IS_CGNAT) + ")";
            case IS_NOT_CGNAT:
                return "NOT EXISTS (" + MEMBERS + ipAddressMatch(bindId, "m.network", FilterOperator.IS_CGNAT) + ")";
            case IS_NULL:
                return "NOT EXISTS (" + MEMBERS + "m.network IS NOT NULL)";
            case IS_NOT_NULL:
                return "EXISTS (" + MEMBERS + "m.network IS NOT NULL)";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for the networks of a segment.");
        }
    }

    private static String asnMatch(String bindId, FilterOperator operator) {
        switch (operator) {
            case NOT_EQUALS_NUMERIC:
                return "NOT EXISTS (" + MEMBERS + "m.asn = :" + bindId + ")";
            case IS_NULL:
                return "NOT EXISTS (" + MEMBERS + "m.asn IS NOT NULL)";
            case IS_NOT_NULL:
                return "EXISTS (" + MEMBERS + "m.asn IS NOT NULL)";
            default:
                return "EXISTS (" + MEMBERS + numericMatch(bindId, "m.asn", operator) + ")";
        }
    }

}
