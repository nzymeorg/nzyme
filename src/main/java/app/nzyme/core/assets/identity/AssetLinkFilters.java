package app.nzyme.core.assets.identity;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class AssetLinkFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "predecessor_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "predecessor_mac", operator), "");
            case "successor_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "successor_mac", operator), "");
            case "kind":
                return GeneratedSql.create(stringMatch(bindId, "kind", operator), "");
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "status", operator), "");
            case "confidence":
                return GeneratedSql.create(numericMatch(bindId, "confidence", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
