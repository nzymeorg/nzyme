package app.nzyme.core.ethernet.recon;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class ReconProbedHostFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "address":
                return GeneratedSql.create(ipAddressMatch(bindId, "target_address", operator), "");
            case "source_count":
                return GeneratedSql.create(numericMatch(bindId, "source_count", operator), "");
            case "port_count":
                return GeneratedSql.create(numericMatch(bindId, "port_count", operator), "");
            case "failed_port_count":
                return GeneratedSql.create(numericMatch(bindId, "failed_port_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
