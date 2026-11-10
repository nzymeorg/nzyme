package app.nzyme.core.dot11.db.filters;

import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class Dot11DiscoFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "disco_type":
                return GeneratedSql.create(stringMatch(bindId, "disco_type", operator), "");
            case "transmitter":
                return GeneratedSql.create(macAddressMatch(bindId, "transmitter", operator), "");
            case "receiver":
                return GeneratedSql.create(macAddressMatch(bindId, "receiver", operator), "");
            case "bssid":
                return GeneratedSql.create(macAddressMatch(bindId, "bssid", operator), "");
            case "reason_code":
                return GeneratedSql.create(numericMatch(bindId, "reason_code", operator), "");
            case "frequency":
                return GeneratedSql.create(numericMatch(bindId, "frequency", operator), "");
            case "signal_strength":
                return GeneratedSql.create(numericMatch(bindId, "signal_strength_average", operator), "");
            case "is_broadcast":
                return GeneratedSql.create(
                        booleanMatch(bindId, "(receiver = '" + Dot11.BROADCAST_MAC + "')", operator), ""
                );
            case "transmitter_is_bssid":
                return GeneratedSql.create(booleanMatch(bindId, "(transmitter = bssid)", operator), "");
            case "bssid_involved":
                return GeneratedSql.create(
                        booleanMatch(bindId, "(transmitter = bssid OR receiver = bssid)", operator), ""
                );
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
