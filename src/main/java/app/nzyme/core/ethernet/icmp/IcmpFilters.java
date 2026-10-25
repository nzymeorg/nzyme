package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "destination_address", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "source_mac", operator), "");
            case "destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "destination_mac", operator), "");
            case "icmp_type":
                return GeneratedSql.create(icmpTypeMatch(bindId, operator), "");
            case "icmp_code":
                return GeneratedSql.create(numericMatch(bindId, "icmp_code", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, "packet_count", operator), "");
            case "redirect_gateway":
                return GeneratedSql.create(ipAddressMatch(bindId, "redirect_gateway", operator), "");
            case "quoted_destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "quoted_destination_address", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
    
    private static String icmpTypeMatch(String bindId, FilterOperator operator) {
        switch (operator) {
            case EQUALS:
                return "icmp_type = :" + bindId + "::integer";
            case NOT_EQUALS:
                return "icmp_type <> :" + bindId + "::integer";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for ICMP type field.");
        }
    }

}
