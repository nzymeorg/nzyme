package app.nzyme.core.ethernet.vpn;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class VPNFilters implements SqlFilterProvider {
    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "session_key":
                return GeneratedSql.create(stringMatch(bindId, "vpn.session_key", operator), "");
            case "client_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(vpn.source_address)", operator));
            case "client_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(vpn.source_mac)", operator));
            case "server_address":
                return GeneratedSql.create("", ipAddressMatch(bindId, "MIN(vpn.destination_address)", operator));
            case "server_mac":
                return GeneratedSql.create("", macAddressMatch(bindId, "MIN(vpn.destination_mac)", operator));
            case "server_port":
                return GeneratedSql.create("", numericMatch(bindId, "MIN(vpn.destination_port)", operator));
            case "type":
                return GeneratedSql.create(stringMatch(bindId, "vpn.vpn_type", operator), "");
            case "transport":
                return GeneratedSql.create(stringMatch(bindId, "vpn.l4_type", operator), "");
            case "handshake_status":
                return GeneratedSql.create(stringMatch(bindId, "vpn.handshake_status", operator), "");
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "vpn.connection_status", operator), "");
            case "message_types":
                return GeneratedSql.create(stringMatch(bindId, "vpn.message_types", operator), "");
            case "provider":
                return GeneratedSql.create(stringMatch(bindId, "COALESCE(vpn.provider, '')", operator), "");
            case "server_name":
                return GeneratedSql.create(stringMatch(bindId, "COALESCE(vpn.server_name, '')", operator), "");
            case "tunneled_bytes":
                return GeneratedSql.create("", numericMatch(bindId, "MAX(vpn.tunneled_bytes)", operator));
            case "duration":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(vpn.most_recent_segment_time) - MIN(vpn.established_at))) * 1000)", operator));

            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }
}
