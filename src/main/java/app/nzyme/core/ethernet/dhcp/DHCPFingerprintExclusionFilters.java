package app.nzyme.core.ethernet.dhcp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DHCPFingerprintExclusionFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "description", operator), "");
            case "enabled":
                return GeneratedSql.create(booleanMatch(bindId, "enabled", operator), "");
            case "mac":
                return GeneratedSql.create(stringMatch(bindId, "mac", operator), "");
            case "match_count":
                return GeneratedSql.create(numericMatch(bindId, "match_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
