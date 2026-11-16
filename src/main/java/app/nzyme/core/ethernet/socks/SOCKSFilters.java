package app.nzyme.core.ethernet.socks;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class SOCKSFilters implements SqlFilterProvider {
    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "session_key":
                return GeneratedSql.create(stringMatch(bindId, "tcp_session_key", operator), "");
            case "client_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(tcp.source_address)", operator));
            case "client_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(tcp.source_mac)", operator));
            case "client_tag":
                return GeneratedSql.create("", assetTagMatch(bindId, "MIN(tcp.source_mac)", operator));
            case "server_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(tcp.destination_address)", operator));
            case "server_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(tcp.destination_mac)", operator));
            case "server_tag":
                return GeneratedSql.create("", assetTagMatch(bindId, "MIN(tcp.destination_mac)", operator));
            case "type":
                return GeneratedSql.create(stringMatch(bindId, "socks_type", operator), "");
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "connection_status", operator), "");
            case "tunneled_bytes":
                return GeneratedSql.create(numericMatch(bindId, "tunneled_bytes", operator), "");
            case "tunneled_destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "tunneled_destination_address", operator), "");
            case "tunneled_destination_host":
                return GeneratedSql.create(stringMatch(bindId, "tunneled_destination_host", operator), "");
            case "tunneled_destination_port":
                return GeneratedSql.create(numericMatch(bindId, "tunneled_destination_port", operator), "");
            case "duration":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(socks.most_recent_segment_time) - MIN(socks.established_at))) * 1000)", operator));

            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
}
