package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class DhcpSpoofingServerFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "mac":
                return GeneratedSql.create(macAddressMatch(bindId, "mac", operator), "");
            case "address":
                return GeneratedSql.create(ipAddressMatch(bindId, "address", operator), "");
            case "trusted":
                return GeneratedSql.create(booleanMatch(bindId, "trusted", operator), "");
            case "response_count":
                return GeneratedSql.create(numericMatch(bindId, "response_count", operator), "");
            case "transaction_count":
                return GeneratedSql.create(numericMatch(bindId, "transaction_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
