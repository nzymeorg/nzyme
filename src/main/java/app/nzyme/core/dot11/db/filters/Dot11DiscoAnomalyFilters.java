package app.nzyme.core.dot11.db.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class Dot11DiscoAnomalyFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "anomaly_type":
                return GeneratedSql.create(stringMatch(bindId, "anomaly_type", operator), "");
            case "bssid":
                return GeneratedSql.create(macAddressMatch(bindId, "bssid", operator), "");
            case "client":
                return GeneratedSql.create(macAddressMatch(bindId, "client", operator), "");
            case "frame_count":
                return GeneratedSql.create(numericMatch(bindId, "frame_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
