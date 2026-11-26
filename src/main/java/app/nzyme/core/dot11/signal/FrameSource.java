package app.nzyme.core.dot11.signal;

import app.nzyme.core.dot11.db.filters.Dot11ChannelSwitchFilters;
import app.nzyme.core.dot11.db.filters.Dot11DiscoFilters;
import app.nzyme.core.util.filters.SqlFilterProvider;

import java.util.function.Supplier;

// Every source is a relation "d" with one row per tap, tuned frequency and record of frames, and the same columns.
public enum FrameSource {

    DISCONNECTION(
            "FROM dot11_disco_frames d WHERE d.created_at >= :tr_from AND d.created_at <= :tr_to " +
                    "AND d.tap_uuid IN (<taps>) AND d.frequency IS NOT NULL " +
                    "AND d.signal_strength_average IS NOT NULL ",
            "d.disco_type",
            "NULL::integer",
            false,
            Dot11DiscoFilters::new
    ),

    // The frequency of a record is the one the tap was tuned to. The channel its frames declare is
    // declared_frequency.
    CHANNEL_SWITCH(
            "FROM (SELECT cs.id, cs.tap_uuid, cs.created_at, cs.created_at AS first_seen, cs.created_at AS last_seen, " +
                    "cs.bssid, cs.bssid AS transmitter, NULL::text AS receiver, cs.frame_type, " +
                    "NULL::integer AS reason_code, false AS is_protected, cs.announcement_type, cs.switch_mode, " +
                    "cs.frequency AS declared_frequency, cs.frame_count AS announcement_frame_count, " +
                    "cs.signal_strength_average AS announcement_signal_strength_average, cs.new_channel, " +
                    "cs.new_frequency, cs.count_min, " +
                    "cs.peak_frames_per_second, cs.anomalies, cs.outcome, t.frequency, t.frame_count, " +
                    "t.signal_strength_average, t.signal_strength_min, t.signal_strength_max, t.signal_histogram " +
                    "FROM dot11_channel_switches cs CROSS JOIN LATERAL (SELECT f.key::int AS frequency, " +
                    "f.value AS signal_histogram, SUM(v.value::bigint) AS frame_count, " +
                    "SUM(v.key::int * v.value::bigint)::float / SUM(v.value::bigint) AS signal_strength_average, " +
                    "MIN(v.key::int) AS signal_strength_min, MAX(v.key::int) AS signal_strength_max " +
                    "FROM jsonb_each(cs.signal_histograms) f CROSS JOIN LATERAL jsonb_each_text(f.value) v " +
                    "GROUP BY f.key, f.value) t) d WHERE d.created_at >= :tr_from AND d.created_at <= :tr_to " +
                    "AND d.tap_uuid IN (<taps>) ",
            "d.frame_type",
            "d.new_channel",
            true,
            Dot11ChannelSwitchFilters::ofSignalRecords
    );

    private final String framesSql;
    private final String frameTypeColumn;
    private final String newChannelColumn;
    private final boolean accessPointsOnly;
    private final Supplier<SqlFilterProvider> filters;

    FrameSource(String framesSql, String frameTypeColumn, String newChannelColumn, boolean accessPointsOnly,
                Supplier<SqlFilterProvider> filters) {
        this.framesSql = framesSql;
        this.frameTypeColumn = frameTypeColumn;
        this.newChannelColumn = newChannelColumn;
        this.accessPointsOnly = accessPointsOnly;
        this.filters = filters;
    }

    String framesSql() {
        return framesSql;
    }

    String frameTypeColumn() {
        return frameTypeColumn;
    }

    String newChannelColumn() {
        return newChannelColumn;
    }

    boolean accessPointsOnly() {
        return accessPointsOnly;
    }

    SqlFilterProvider filters() {
        return filters.get();
    }

}
