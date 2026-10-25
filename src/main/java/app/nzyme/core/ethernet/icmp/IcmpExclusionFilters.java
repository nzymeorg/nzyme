package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class IcmpExclusionFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "description", operator), "");
            case "enabled":
                return GeneratedSql.create(booleanMatch(bindId, "enabled", operator), "");
            case "source_cidr":
                return GeneratedSql.create(stringMatch(bindId, "source_cidr", operator), "");
            case "source_mac":
                return GeneratedSql.create(stringMatch(bindId, "source_mac", operator), "");
            case "gateway_cidr":
                return GeneratedSql.create(stringMatch(bindId, "gateway_cidr", operator), "");
            case "match_count":
                return GeneratedSql.create(numericMatch(bindId, "match_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
