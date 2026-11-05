package app.nzyme.core.assets.profile;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.stringMatch;

public class AssetTimelineFilters implements SqlFilterProvider {

    private static final String KIND = "(CASE WHEN t.type = 'ATTRIBUTE' THEN 'ATTRIBUTE:' || t.detail ELSE t.type END)";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "type":
                return GeneratedSql.create(stringMatch(bindId, KIND, operator), "");
            case "value":
                return GeneratedSql.create(stringMatch(bindId, "t.value", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
