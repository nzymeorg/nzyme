package app.nzyme.core.ethernet.dhcp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DHCPFilters implements SqlFilterProvider {
    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "transaction_type":
                return GeneratedSql.create(stringMatch(bindId, "transaction_type", operator), "");
            case "client_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "client_mac", operator), "");
            case "client_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "client_mac", operator), "");
            case "server_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "server_mac", operator), "");
            case "server_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "server_mac", operator), "");
            case "requested_ip":
                return GeneratedSql.create(ipAddressMatch(bindId, "requested_ip_address", operator), "");
            case "fingerprint":
                return GeneratedSql.create(stringMatch(bindId, "fingerprint", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
}
