package app.nzyme.core.ethernet.webrtc;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class WebRTCFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "id":
                return GeneratedSql.create(stringMatch(bindId, "w.negotiation_key_sha256", operator), "");
                case "has_rtp":
                return GeneratedSql.create("", booleanMatch(bindId, "BOOL_OR(w.has_rtp)", operator));
            case "has_dtls":
                return GeneratedSql.create("", booleanMatch(bindId, "BOOL_OR(w.has_dtls)", operator));
            case "has_audio":
                return GeneratedSql.create("", booleanMatch(bindId, "BOOL_OR(w.has_audio)", operator));
            case "has_video":
                return GeneratedSql.create("", booleanMatch(bindId, "BOOL_OR(w.has_video)", operator));
            case "active":
                return GeneratedSql.create("", booleanMatch(bindId, "(MAX(w.last_activity) >= NOW() - INTERVAL '60 seconds')", operator));
            case "stream_count":
                return GeneratedSql.create("", numericMatch(bindId, "MAX(w.stream_count)", operator));
            case "bytes_exchanged":
                return GeneratedSql.create("", numericMatch(bindId, "MAX(sb.bytes_exchanged)", operator));
            case "duration_ms":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(w.last_activity) - MIN(w.first_seen))) * 1000)::bigint", operator));
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "s.source_mac", operator), "");
            case "source_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "s.source_mac", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "s.source_address", operator), "");
            case "destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "s.destination_mac", operator), "");
            case "destination_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "s.destination_mac", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "s.destination_address", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}