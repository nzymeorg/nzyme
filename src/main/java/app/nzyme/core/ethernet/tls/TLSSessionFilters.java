package app.nzyme.core.ethernet.tls;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class TLSSessionFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "session_key":
                return GeneratedSql.create(stringMatch(bindId, "tls.tcp_session_key", operator), "");
            case "client_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "tls.source_address", operator), "");
            case "client_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "tls.source_mac", operator), "");
            case "client_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "tls.source_mac", operator), "");
            case "server_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "tls.destination_address", operator), "");
            case "server_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "tls.destination_mac", operator), "");
            case "server_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "tls.destination_mac", operator), "");
            case "server_port":
                return GeneratedSql.create(numericMatch(bindId, "tls.destination_port", operator), "");
            case "server_name":
                return GeneratedSql.create(stringMatch(bindId, "tls.server_name", operator), "");
            case "version":
                return GeneratedSql.create(stringMatch(bindId, "tls.version", operator), "");
            case "client_max_version":
                return GeneratedSql.create(stringMatch(bindId, "tls.client_max_version", operator), "");
            case "cipher_suite":
                return GeneratedSql.create(numericMatch(bindId, "tls.cipher_suite", operator), "");
            case "alpn":
                return GeneratedSql.create(stringMatch(bindId, "tls.alpn_selected", operator), "");
            case "alpn_offered":
                return GeneratedSql.create(stringMatch(bindId, "(tls.client_hello->>'alpn')", operator), "");
            case "connection_status":
                return GeneratedSql.create(stringMatch(bindId, "tls.connection_status", operator), "");
            case "bytes":
                return GeneratedSql.create(numericMatch(bindId, "tls.bytes_count", operator), "");
            case "duration":
                return GeneratedSql.create("", numericMatch(bindId, "(EXTRACT(EPOCH FROM (MAX(tls.most_recent_segment_time) - MIN(tls.established_at))) * 1000)", operator));
            case "leaf_fingerprint":
                return GeneratedSql.create(stringMatch(bindId, "encode(tls.server_leaf_sha256, 'hex')", operator), "");
            case "leaf_common_name":
                return GeneratedSql.create(stringMatch(bindId, "leaf.common_name", operator), "");
            case "leaf_issuer":
                return GeneratedSql.create(stringMatch(bindId, "leaf.issuer", operator), "");
            case "chain_trusted":
                return GeneratedSql.create(booleanMatch(bindId, "tls.chain_trusted", operator), "");
            case "sni_matches_certificate":
                return GeneratedSql.create(booleanMatch(bindId, "tls.sni_matches_certificate", operator), "");
            case "leaf_valid_at_handshake":
                return GeneratedSql.create(booleanMatch(bindId, "tls.leaf_valid_at_handshake", operator), "");
            case "leaf_self_signed":
                return GeneratedSql.create(booleanMatch(bindId, "tls.server_leaf_self_signed", operator), "");
            case "session_resumed":
                return GeneratedSql.create(booleanMatch(bindId, "tls.session_resumed", operator), "");
            case "client_certificate_requested":
                return GeneratedSql.create(booleanMatch(bindId, "tls.client_certificate_requested", operator), "");
            case "encrypted_client_hello":
                return GeneratedSql.create(booleanMatch(bindId, "tls.encrypted_client_hello", operator), "");
            case "chain_trust_error":
                return GeneratedSql.create(stringMatch(bindId, "tls.chain_trust_error", operator), "");
            case "ja4":
                return GeneratedSql.create(stringMatch(bindId, "tls.ja4", operator), "");
            case "alert":
                return GeneratedSql.create(alertMatch(bindId, operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    /*
     * Alerts are stored as a JSON array per session. Encrypted alerts carry no usable description (they are
     * almost always the close_notify at the end of a session), so only alerts visible on the wire are matched.
     * The value is the numeric alert description from RFC 8446.
     */
    private static String alertMatch(String bindId, FilterOperator operator) {
        String visible = "SELECT 1 FROM jsonb_array_elements(tls.alerts) AS alert " +
                "WHERE NOT COALESCE((alert ->> 'encrypted')::boolean, false)";

        switch (operator) {
            case CONTAINS:
                return "EXISTS (" + visible + " AND (alert ->> 'description')::int = :" + bindId + "::int)";
            case NOT_CONTAINS:
                return "NOT EXISTS (" + visible + " AND (alert ->> 'description')::int = :" + bindId + "::int)";
            case IS_EMPTY:
                return "NOT EXISTS (" + visible + ")";
            case IS_NOT_EMPTY:
                return "EXISTS (" + visible + ")";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for TLS alert field.");
        }
    }

}
