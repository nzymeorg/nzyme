package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DnsTunnelFindingFilters implements SqlFilterProvider {

    private final String prefix;

    public DnsTunnelFindingFilters() {
        this(null);
    }

    public DnsTunnelFindingFilters(String tableAlias) {
        this.prefix = tableAlias == null ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "client_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "client_address", operator), "");
            case "client_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "client_mac", operator), "");
            case "domain":
                return GeneratedSql.create(stringMatch(bindId, prefix + "domain", operator), "");
            case "query_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "query_count", operator), "");
            case "distinct_name_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "distinct_name_count", operator), "");
            case "negative_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "negative_count", operator), "");
            case "indicator":
                return GeneratedSql.create(indicatorMatch(bindId, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    // Indicators are a JSON array of strings. Equality is a membership test, everything else works on the text.
    private String indicatorMatch(String bindId, FilterOperator operator) {
        String contains = "jsonb_exists(" + prefix + "indicators, :" + bindId + ")";
        switch (operator) {
            case EQUALS:
                return contains;
            case NOT_EQUALS:
                return "NOT " + contains;
            default:
                return stringMatch(bindId, prefix + "indicators::text", operator);
        }
    }

}
