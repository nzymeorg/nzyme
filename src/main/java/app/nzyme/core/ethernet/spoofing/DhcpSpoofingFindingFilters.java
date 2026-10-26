package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DhcpSpoofingFindingFilters implements SqlFilterProvider {

    private final String prefix;

    public DhcpSpoofingFindingFilters() {
        this(null);
    }

    public DhcpSpoofingFindingFilters(String tableAlias) {
        this.prefix = tableAlias == null ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "type":
                return GeneratedSql.create(stringMatch(bindId, prefix + "type", operator), "");
            case "server_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "server_mac", operator), "");
            case "server_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "server_address", operator), "");
            case "response_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "response_count", operator), "");
            case "transaction_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "transaction_count", operator), "");
            case "victim_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "victim_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
