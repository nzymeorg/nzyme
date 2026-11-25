package app.nzyme.core.ethernet.ipv6transition;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class Ipv6TransitionFilters implements SqlFilterProvider {
    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "session_key":
                return GeneratedSql.create(stringMatch(bindId, "itt.session_key", operator), "");
            case "client_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(itt.source_address)", operator));
            case "client_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(itt.source_mac)", operator));
            case "client_tag":
                return GeneratedSql.create("", assetTagMatch(bindId, "MIN(itt.source_mac)", operator));
            case "server_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(itt.destination_address)", operator));
            case "server_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(itt.destination_mac)", operator));
            case "server_tag":
                return GeneratedSql.create("", assetTagMatch(bindId, "MIN(itt.destination_mac)", operator));
            case "server_port":
                return GeneratedSql.create("", numericMatch(bindId, "MIN(itt.destination_port)", operator));
            case "type":
                return GeneratedSql.create(stringMatch(bindId, "itt.tunnel_type", operator), "");
            case "transport":
                return GeneratedSql.create(stringMatch(bindId, "itt.transport", operator), "");
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "itt.connection_status", operator), "");
            case "tunneled_bytes":
                return GeneratedSql.create("", numericMatch(bindId, "MAX(itt.tunneled_bytes)", operator));
            case "duration":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(itt.most_recent_segment_time) - MIN(itt.established_at))) * 1000)", operator));

            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
}
