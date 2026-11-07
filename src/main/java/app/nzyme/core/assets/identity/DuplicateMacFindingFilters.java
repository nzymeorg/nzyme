package app.nzyme.core.assets.identity;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DuplicateMacFindingFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "mac":
                return GeneratedSql.create(macAddressMatch(bindId, "mac", operator), "");
            case "stack_count":
                return GeneratedSql.create(numericMatch(bindId, "stack_count", operator), "");
            case "session_count":
                return GeneratedSql.create(numericMatch(bindId, "session_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
