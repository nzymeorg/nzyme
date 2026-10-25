package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpTracerouteFilters implements SqlFilterProvider {

    private final String prefix;

    public IcmpTracerouteFilters() {
        this("");
    }

    public IcmpTracerouteFilters(String tableAlias) {
        this.prefix = tableAlias == null || tableAlias.isEmpty() ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "uuid":
                return GeneratedSql.create(uuidMatch(bindId, prefix + "uuid", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "source_address", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "source_mac", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "destination_address", operator), "");
            case "protocol":
                return GeneratedSql.create(protocolMatch(bindId, prefix + "protocol", operator), "");
            case "destination_reached":
                return GeneratedSql.create(booleanMatch(bindId, prefix + "destination_reached", operator), "");
            case "router_count":
                return GeneratedSql.create(numericMatch(bindId, "jsonb_array_length(" + prefix + "routers)", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "packet_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    private static String protocolMatch(String bindId, String column, FilterOperator operator) {
        switch (operator) {
            case EQUALS:
                return column + " = :" + bindId + "::integer";
            case NOT_EQUALS:
                return column + " <> :" + bindId + "::integer";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for protocol field.");
        }
    }

}
