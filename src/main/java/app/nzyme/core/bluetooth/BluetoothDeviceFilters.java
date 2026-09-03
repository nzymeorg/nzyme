package app.nzyme.core.bluetooth;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.jsonbObjectKeyMatch;
import static app.nzyme.core.util.filters.FilterSql.stringMatch;

public class BluetoothDeviceFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "mac":
                return GeneratedSql.create(stringMatch(bindId, "mac", operator), "");
            case "ouis":
                return GeneratedSql.create(stringMatch(bindId, "oui", operator), "");
            case "manufacturer_names":
                return GeneratedSql.create(stringMatch(bindId, "manufacturer_name", operator), "");
            case "tags":
                return GeneratedSql.create(jsonbObjectKeyMatch(bindId, "tags", operator), "");
            case "transports":
                return GeneratedSql.create(stringMatch(bindId, "transport", operator), "");
            case "names":
                return GeneratedSql.create(stringMatch(bindId, "name", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
