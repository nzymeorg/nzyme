package app.nzyme.core.ethernet.beacons;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class BeaconCheckinFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "s.source_address", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "s.source_mac", operator), "");
            case "source_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "s.source_mac", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "c.destination_address", operator), "");
            case "destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "c.destination_mac", operator), "");
            case "destination_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "c.destination_mac", operator), "");
            case "bytes_total":
                return GeneratedSql.create(numericMatch(bindId, "c.bytes_total", operator), "");
            case "session_count":
                return GeneratedSql.create(numericMatch(bindId, "c.session_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
