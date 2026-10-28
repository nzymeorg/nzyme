package app.nzyme.core.ethernet.vpn.db;

import app.nzyme.core.ethernet.L4MapperTools;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class VpnTunnelEntryMapper implements RowMapper<VpnTunnelEntry> {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Override
    public VpnTunnelEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String messageTypesS = rs.getString("message_types");
        List<String> messageTypes = messageTypesS == null || messageTypesS.isEmpty()
                ? Collections.emptyList() : Arrays.asList(messageTypesS.split(","));

        String detailsS = rs.getString("details");
        Map<String, String> details;
        try {
            details = detailsS == null ? Collections.emptyMap() : om.readValue(detailsS, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("Could not parse VPN tunnel details.", e);
        }

        return VpnTunnelEntry.create(
                rs.getString("session_key"),
                rs.getString("l4_type"),
                rs.getString("vpn_type"),
                rs.getString("handshake_status"),
                messageTypes,
                rs.getString("provider"),
                rs.getString("server_name"),
                details,
                rs.getString("connection_status"),
                rs.getLong("tunneled_bytes"),
                L4MapperTools.fieldsToAddressData("source", rs),
                L4MapperTools.fieldsToAddressData("destination", rs),
                new DateTime(rs.getTimestamp("established_at")),
                rs.getTimestamp("terminated_at") == null ? null : new DateTime(rs.getTimestamp("terminated_at")),
                new DateTime(rs.getTimestamp("most_recent_segment_time")),
                rs.getLong("duration_ms")
        );
    }

}
