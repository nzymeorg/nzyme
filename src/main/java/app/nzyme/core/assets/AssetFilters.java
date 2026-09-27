package app.nzyme.core.assets;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class AssetFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "mac":
                return GeneratedSql.create(macAddressMatch(bindId, "a.mac", operator), "");
            case "hostname":
                return GeneratedSql.create("", anyStringMatch(bindId, "h.hostname", operator));
            case "ip_address":
                return GeneratedSql.create("", anyIpAddressMatch(bindId, "i.address", operator));
            case "is_active":
                return GeneratedSql.create(booleanMatch(bindId, AssetManager.ACTIVE_CONDITION, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }    }

}
