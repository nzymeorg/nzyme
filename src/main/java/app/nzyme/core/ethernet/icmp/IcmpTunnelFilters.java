package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpTunnelFilters implements SqlFilterProvider {

    private final String prefix;

    public IcmpTunnelFilters() {
        this("");
    }

    public IcmpTunnelFilters(String tableAlias) {
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
            case "max_payload_bytes":
                return GeneratedSql.create(numericMatch(bindId, prefix + "max_payload_bytes", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "packet_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
