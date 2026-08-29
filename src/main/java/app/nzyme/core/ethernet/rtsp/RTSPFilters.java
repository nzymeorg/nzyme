package app.nzyme.core.ethernet.rtsp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;
import static app.nzyme.core.util.filters.FilterSql.ipAddressMatch;
import static app.nzyme.core.util.filters.FilterSql.macAddressMatch;

public class RTSPFilters implements SqlFilterProvider  {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "id":
                return GeneratedSql.create(stringMatch(bindId, "rtsp.setup_tcp_session_key", operator), "");
            case "type":
                return GeneratedSql.create(stringMatch(bindId, "stream.l4_type", operator), "");
            case "stream_source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "stream.source_mac", operator), "");
            case "stream_source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "stream.source_address", operator), "");
            case "stream_destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "stream.destination_mac", operator), "");
            case "stream_destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "stream.destination_address", operator), "");
            case "bytes_rx_count":
                return GeneratedSql.create(numericMatch(bindId, "stream.bytes_rx_count", operator), "");
            case "bytes_tx_count":
                return GeneratedSql.create(numericMatch(bindId, "stream.bytes_tx_count", operator), "");
            case "duration_ms":
                String durationExpr = // Only way to do this with Postgres.
                        "CASE WHEN MIN(rtsp.setup_established_at) IS NOT NULL " +
                                "AND MAX(rtsp.setup_most_recent_segment_time) IS NOT NULL " +
                                "AND MAX(stream.most_recent_segment_time) IS NOT NULL " +
                                "THEN (EXTRACT(EPOCH FROM (GREATEST(MAX(rtsp.setup_most_recent_segment_time), " +
                                "MAX(stream.most_recent_segment_time)) - MIN(rtsp.setup_established_at))) * 1000)::bigint " +
                                "ELSE NULL END";
                return GeneratedSql.create("", numericMatch(bindId, durationExpr, operator));
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
