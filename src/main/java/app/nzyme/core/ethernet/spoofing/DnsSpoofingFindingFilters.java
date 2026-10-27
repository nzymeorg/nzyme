package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DnsSpoofingFindingFilters implements SqlFilterProvider {

    private final String prefix;

    public DnsSpoofingFindingFilters() {
        this(null);
    }

    public DnsSpoofingFindingFilters(String tableAlias) {
        this.prefix = tableAlias == null ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "server_address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "server_address", operator), "");
            case "spoofer_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "spoofer_mac", operator), "");
            case "owner_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "owner_mac", operator), "");
            case "forged_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "forged_count", operator), "");
            case "won_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "won_count", operator), "");
            case "name_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "name_count", operator), "");
            case "victim_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "victim_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
