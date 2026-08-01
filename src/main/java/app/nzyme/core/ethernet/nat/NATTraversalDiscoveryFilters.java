package app.nzyme.core.ethernet.nat;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

public class NATTraversalDiscoveryFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
