package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpRedirectFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "uuid":
                return GeneratedSql.create(uuidMatch(bindId, "uuid", operator), "");
            case "sender_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "sender_address", operator), "");
            case "sender_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "sender_mac", operator), "");
            case "gateway_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "gateway_address", operator), "");
            case "target_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "target_address", operator), "");
            case "target_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "target_mac", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, "packet_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
