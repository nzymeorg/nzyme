package app.nzyme.core.assets.tags;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.numericMatch;
import static app.nzyme.core.util.filters.FilterSql.stringMatch;

public class AssetTagFilters implements SqlFilterProvider {

    public static final String ASSET_COUNT = "(SELECT COUNT(*) FROM assets_tag_assignments AS a WHERE a.tag_id = t.id)";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "t.name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "t.description", operator), "");
            case "asset_count":
                return GeneratedSql.create(numericMatch(bindId, ASSET_COUNT, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
