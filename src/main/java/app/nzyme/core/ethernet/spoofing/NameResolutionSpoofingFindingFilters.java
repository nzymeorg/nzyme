package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class NameResolutionSpoofingFindingFilters implements SqlFilterProvider {

    private final String prefix;

    public NameResolutionSpoofingFindingFilters() {
        this(null);
    }

    public NameResolutionSpoofingFindingFilters(String tableAlias) {
        this.prefix = tableAlias == null ? "" : tableAlias + ".";
    }

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "responder_mac":
                return GeneratedSql.create(macAddressMatch(bindId, prefix + "responder_mac", operator), "");
            case "name_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "name_count", operator), "");
            case "response_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "response_count", operator), "");
            case "victim_count":
                return GeneratedSql.create(numericMatch(bindId, prefix + "victim_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
