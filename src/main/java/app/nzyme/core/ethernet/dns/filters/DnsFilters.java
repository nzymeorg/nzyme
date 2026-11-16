package app.nzyme.core.ethernet.dns.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DnsFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "query_value":
                return GeneratedSql.create("dns_type = 'response' OR (dns_type = 'query' AND (" + stringMatch(bindId, "data_value", operator) + "))", "");
            case "query_type":
                return GeneratedSql.create("dns_type = 'response' OR (dns_type = 'query' AND (" + stringMatch(bindId, "data_type", operator) + "))", "");
            case "query_etld":
                return GeneratedSql.create("dns_type = 'response' OR (dns_type = 'query' AND (" + stringMatch(bindId, "data_value_etld", operator) + "))", "");
            case "client_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "client_address", operator), "");
            case "server_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "server_address", operator), "");
            case "server_port":
                return GeneratedSql.create(numericMatch(bindId, "server_port", operator), "");
            case "client_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "client_mac", operator), "");
            case "client_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "client_mac", operator), "");
            case "server_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "server_mac", operator), "");
            case "server_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "server_mac", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
