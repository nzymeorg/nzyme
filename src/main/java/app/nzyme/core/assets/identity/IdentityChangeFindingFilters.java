package app.nzyme.core.assets.identity;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IdentityChangeFindingFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "mac":
                return GeneratedSql.create(macAddressMatch(bindId, "mac", operator), "");
            case "score":
                return GeneratedSql.create(numericMatch(bindId, "score", operator), "");
            case "previous_fingerprint":
                return GeneratedSql.create(stringMatch(bindId, "previous_fingerprint", operator), "");
            case "new_fingerprint":
                return GeneratedSql.create(stringMatch(bindId, "new_fingerprint", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
