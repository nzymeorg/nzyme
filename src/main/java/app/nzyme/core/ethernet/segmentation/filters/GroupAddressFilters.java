package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class GroupAddressFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "address":
                return GeneratedSql.create(ipAddressMatch(bindId, "address", operator), "");
            case "sessions_from":
                return GeneratedSql.create(numericMatch(bindId, "sessions_from", operator), "");
            case "sessions_to":
                return GeneratedSql.create(numericMatch(bindId, "sessions_to", operator), "");
            case "answered":
                return GeneratedSql.create(numericMatch(bindId, "answered", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
