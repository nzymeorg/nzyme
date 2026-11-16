package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class PolicyFilters implements SqlFilterProvider {

    public static final String CROSSED_FINDINGS = "COALESCE(f.crossed_findings, 0)";
    public static final String ATTEMPTED_FINDINGS = "COALESCE(f.attempted_findings, 0)";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "name":
                return GeneratedSql.create(stringMatch(bindId, "p.name", operator), "");
            case "description":
                return GeneratedSql.create(stringMatch(bindId, "p.description", operator), "");
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "p.status", operator), "");
            case "alert_on_connected":
                return GeneratedSql.create(booleanMatch(bindId, "p.alert_on_connected", operator), "");
            case "alert_on_attempted":
                return GeneratedSql.create(booleanMatch(bindId, "p.alert_on_attempted", operator), "");
            case "crossed_findings":
                return GeneratedSql.create(numericMatch(bindId, CROSSED_FINDINGS, operator), "");
            case "attempted_findings":
                return GeneratedSql.create(numericMatch(bindId, ATTEMPTED_FINDINGS, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
