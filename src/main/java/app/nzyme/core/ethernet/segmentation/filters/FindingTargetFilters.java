package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class FindingTargetFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "address":
                return GeneratedSql.create(ipAddressMatch(bindId, "t.address", operator), "");
            case "l4_type":
                return GeneratedSql.create(stringMatch(bindId, "t.l4_type", operator), "");
            case "port":
                return GeneratedSql.create(numericMatch(bindId, "t.port", operator), "");
            case "protocol":
                return GeneratedSql.create(stringMatch(bindId, "t.protocol", operator), "");
            case "outcome":
                return GeneratedSql.create(stringMatch(bindId, "t.outcome", operator), "");
            case "sessions":
                return GeneratedSql.create(numericMatch(bindId, "t.sessions", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
