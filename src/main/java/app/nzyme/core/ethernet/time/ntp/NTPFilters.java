package app.nzyme.core.ethernet.time.ntp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class NTPFilters implements SqlFilterProvider {
    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "transaction_key":
                return GeneratedSql.create(stringMatch(bindId, "transaction_key", operator), "");
            case "client_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "client_mac", operator), "");
            case "client_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "client_mac", operator), "");
            case "client_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "client_address", operator), "");
            case "client_port":
                return GeneratedSql.create(numericMatch(bindId, "client_port", operator), "");
            case "server_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "server_mac", operator),"");
            case "server_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "server_mac", operator),"");
            case "server_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "server_address", operator), "");
            case "server_port":
                return GeneratedSql.create(numericMatch(bindId, "server_port", operator), "");
            case "stratum":
                return GeneratedSql.create(numericMatch(bindId, "stratum", operator), "");
            case "reference_id":
                return GeneratedSql.create(stringMatch(bindId, "reference_id", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
}
