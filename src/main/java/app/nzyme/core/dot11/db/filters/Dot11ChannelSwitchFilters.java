package app.nzyme.core.dot11.db.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class Dot11ChannelSwitchFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "bssid":
                return GeneratedSql.create(macAddressMatch(bindId, "bssid", operator), "");
            case "frame_type":
                return GeneratedSql.create(stringMatch(bindId, "frame_type", operator), "");
            case "announcement_type":
                return GeneratedSql.create(stringMatch(bindId, "announcement_type", operator), "");
            case "switch_mode":
                return GeneratedSql.create(numericMatch(bindId, "switch_mode", operator), "");
            case "frequency":
                return GeneratedSql.create(numericMatch(bindId, "frequency", operator), "");
            case "new_channel":
                return GeneratedSql.create(numericMatch(bindId, "new_channel", operator), "");
            case "new_frequency":
                return GeneratedSql.create(numericMatch(bindId, "new_frequency", operator), "");
            case "count":
                return GeneratedSql.create(numericMatch(bindId, "count_min", operator), "");
            case "frame_count":
                return GeneratedSql.create(numericMatch(bindId, "frame_count", operator), "");
            case "peak_rate":
                return GeneratedSql.create(numericMatch(bindId, "peak_frames_per_second", operator), "");
            case "signal_strength":
                return GeneratedSql.create(numericMatch(bindId, "signal_strength_average", operator), "");
            case "anomaly":
                return GeneratedSql.create(textArrayStringMatch(bindId, "anomalies", operator), "");
            case "has_anomalies":
                return GeneratedSql.create(booleanMatch(bindId, "(cardinality(anomalies) > 0)", operator), "");
            case "outcome":
                return GeneratedSql.create(stringMatch(bindId, "outcome", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
