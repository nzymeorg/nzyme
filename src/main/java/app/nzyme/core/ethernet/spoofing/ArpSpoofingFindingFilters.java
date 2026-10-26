package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class ArpSpoofingFindingFilters implements SqlFilterProvider {

    private final String prefix;

    public ArpSpoofingFindingFilters() {
        this(null);
    }

    public ArpSpoofingFindingFilters(String tableAlias) {
        this.prefix = tableAlias == null ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "address":
                return GeneratedSql.create(ipAddressMatch(bindId, prefix + "address", operator), "");
            case "spoofer_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "spoofer_mac", operator), "");
            case "owner_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "owner_mac", operator), "");
            case "claim_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "claim_count", operator), "");
            case "unsolicited_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "unsolicited_count", operator), "");
            case "victim_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "victim_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
