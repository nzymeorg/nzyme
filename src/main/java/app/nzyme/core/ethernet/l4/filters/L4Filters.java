package app.nzyme.core.ethernet.l4.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class L4Filters implements SqlFilterProvider  {

    private interface Matcher {
        String match(String bindId, String column, FilterOperator operator);
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "address":
                return GeneratedSql.create(eitherSide(bindId, "address", operator, (b, c, o) -> ipAddressMatch(b, c, o)), "");
            case "address_geo_asn_number":
                return GeneratedSql.create(eitherSide(bindId, "address_geo_asn_number", operator, (b, c, o) -> numericMatch(b, c, o)), "");
            case "address_geo_country_code":
                return GeneratedSql.create(eitherSide(bindId, "address_geo_country_code", operator, (b, c, o) -> stringMatch(b, c, o)), "");
            case "address_geo_city":
                return GeneratedSql.create(eitherSide(bindId, "address_geo_city", operator, (b, c, o) -> stringMatch(b, c, o)), "");
            case "l4_type":
                return GeneratedSql.create(stringMatch(bindId, "l4_Type", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "source_mac", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "source_port":
                return GeneratedSql.create(numericMatch(bindId, "source_port", operator), "");
            case "destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "destination_mac", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "destination_address", operator), "");
            case "destination_port":
                return GeneratedSql.create(numericMatch(bindId, "destination_port", operator), "");
            case "bytes_rx_count":
                return GeneratedSql.create(numericMatch(bindId, "bytes_rx_count", operator), "");
            case "bytes_tx_count":
                return GeneratedSql.create(numericMatch(bindId, "bytes_tx_count", operator), "");
            case "segments_count":
                return GeneratedSql.create(numericMatch(bindId, "segments_count", operator), "");
            case "state":
                return GeneratedSql.create(stringMatch(bindId, "state", operator), "");
            case "session_key":
                return GeneratedSql.create(stringMatch(bindId, "session_key", operator), "");
            case "source_address_geo_asn_number":
                return GeneratedSql.create(numericMatch(bindId, "source_address_geo_asn_number", operator), "");
            case "source_address_geo_city":
                return GeneratedSql.create(stringMatch(bindId, "source_address_geo_city", operator), "");
            case "source_address_geo_country_code":
                return GeneratedSql.create(stringMatch(bindId, "source_address_geo_country_code", operator), "");
            case "destination_address_geo_asn_number":
                return GeneratedSql.create(numericMatch(bindId, "destination_address_geo_asn_number", operator), "");
            case "destination_address_geo_city":
                return GeneratedSql.create(stringMatch(bindId, "destination_address_geo_city", operator), "");
            case "destination_address_geo_country_code":
                return GeneratedSql.create(stringMatch(bindId, "destination_address_geo_country_code", operator), "");
            case "fingerprint":
                return GeneratedSql.create(stringMatch(bindId, "fingerprint", operator), "");
            case "duration":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(most_recent_segment_time) - MIN(start_time))) * 1000)", operator));
            case "tags":
                return GeneratedSql.create(jsonbStringMatch(bindId, "tags", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    /*
     * A positive operator matches if either end matches. A negative operator matches if neither end matches its
     * positive counterpart, which keeps sessions with an end that has no value at all, like the autonomous system
     * of an internal address. Comparing both ends with the negative operator would drop them.
     *
     * IS_NOT_PRIVATE is not a negative operator here: it matches if either end is public.
     */
    private static String eitherSide(String bindId, String field, FilterOperator operator, Matcher matcher) {
        FilterOperator positive = positiveCounterpart(operator);

        String either = "(" + matcher.match(bindId, "source_" + field, positive == null ? operator : positive)
                + ") OR (" + matcher.match(bindId, "destination_" + field, positive == null ? operator : positive) + ")";

        return positive == null ? "(" + either + ")" : "((" + either + ") IS NOT TRUE)";
    }

    private static FilterOperator positiveCounterpart(FilterOperator operator) {
        switch (operator) {
            case NOT_EQUALS:
                return FilterOperator.EQUALS;
            case NOT_EQUALS_NUMERIC:
                return FilterOperator.EQUALS_NUMERIC;
            case NOT_REGEX_MATCH:
                return FilterOperator.REGEX_MATCH;
            case NOT_IN_CIDR:
                return FilterOperator.IN_CIDR;
            case NOT_CONTAINS:
                return FilterOperator.CONTAINS;
            default:
                return null;
        }
    }

}
