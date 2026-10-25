package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpUnreachableHostFilters implements SqlFilterProvider {

    private final String prefix;

    public IcmpUnreachableHostFilters() {
        this("");
    }

    public IcmpUnreachableHostFilters(String tableAlias) {
        this.prefix = tableAlias == null || tableAlias.isEmpty() ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "unreachable_host":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "quoted_destination_address", operator), "");
            case "source":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "destination_address", operator), "");
            case "reporting_router":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "source_address", operator), "");
            case "protocol":
                return GeneratedSql.create(protocolMatch(bindId, prefix + "quoted_protocol", operator), "");
            case "code":
                return GeneratedSql.create(numericMatch(bindId, prefix + "icmp_code", operator), "");
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
