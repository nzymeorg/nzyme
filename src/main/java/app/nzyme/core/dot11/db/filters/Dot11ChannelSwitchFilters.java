package app.nzyme.core.dot11.db.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

import java.util.Map;

public class Dot11ChannelSwitchFilters implements SqlFilterProvider {

    private final Map<String, String> renamedColumns;

    public Dot11ChannelSwitchFilters() {
        this(Map.of());
    }

    private Dot11ChannelSwitchFilters(Map<String, String> renamedColumns) {
        this.renamedColumns = renamedColumns;
    }

    /*
     * A signal record is the part of an announcement a tap recorded while tuned to one frequency. It has that
     * frequency, its frames and their signal strength as its own, next to those of the whole announcement, which
     * the filters are about.
     */
    public static Dot11ChannelSwitchFilters ofSignalRecords() {
        return new Dot11ChannelSwitchFilters(Map.of(
                "frequency", "declared_frequency",
                "frame_count", "announcement_frame_count",
                "signal_strength_average", "announcement_signal_strength_average"
        ));
    }

    private String column(String name) {
        return renamedColumns.getOrDefault(name, name);
    }

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
                return GeneratedSql.create(numericMatch(bindId, column("frequency"), operator), "");
            case "new_channel":
                return GeneratedSql.create(numericMatch(bindId, "new_channel", operator), "");
            case "new_frequency":
                return GeneratedSql.create(numericMatch(bindId, "new_frequency", operator), "");
            case "count":
                return GeneratedSql.create(numericMatch(bindId, "count_min", operator), "");
            case "frame_count":
                return GeneratedSql.create(numericMatch(bindId, column("frame_count"), operator), "");
            case "peak_rate":
                return GeneratedSql.create(numericMatch(bindId, "peak_frames_per_second", operator), "");
            case "signal_strength":
                return GeneratedSql.create(numericMatch(bindId, column("signal_strength_average"), operator), "");
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
