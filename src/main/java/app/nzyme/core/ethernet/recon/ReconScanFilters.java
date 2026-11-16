package app.nzyme.core.ethernet.recon;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class ReconScanFilters implements SqlFilterProvider {

    private final String prefix;

    public ReconScanFilters() {
        this("");
    }

    public ReconScanFilters(String tableAlias) {
        this.prefix = tableAlias == null || tableAlias.isEmpty() ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "type":
                return GeneratedSql.create(stringMatch(bindId, prefix + "type", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "source_address", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "source_mac", operator), "");
            case "source_tag":
                return GeneratedSql.create(assetTagMatch(bindId, prefix + "source_mac", operator), "");
            case "target_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "target_address", operator), "");
            case "host_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "host_count", operator), "");
            case "port_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "port_count", operator), "");
            case "probe_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "probe_count", operator), "");
            case "open_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "open_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
