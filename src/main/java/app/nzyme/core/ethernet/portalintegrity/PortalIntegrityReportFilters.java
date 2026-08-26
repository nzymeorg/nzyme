package app.nzyme.core.ethernet.portalintegrity;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class PortalIntegrityReportFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "uuid":
                return GeneratedSql.create(uuidMatch(bindId, "r.uuid", operator), "");
            case "probe_name":
                return GeneratedSql.create(stringMatch(bindId, "r.probe_name", operator), "");
            case "control_url":
                return GeneratedSql.create(stringMatch(bindId, "r.control_url", operator), "");
            case "last_hop_url":
                return GeneratedSql.create(stringMatch(bindId, "last_hop_url", operator), "");
            case "hop_count":
                return GeneratedSql.create(numericMatch(bindId, "hop_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
