package app.nzyme.core.ethernet.ipv6transition.db;

import app.nzyme.core.ethernet.L4MapperTools;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

public class Ipv6TransitionTunnelEntryMapper implements RowMapper<Ipv6TransitionTunnelEntry> {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Override
    public Ipv6TransitionTunnelEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String detailsS = rs.getString("details");
        Map<String, String> details;
        try {
            details = detailsS == null ? Collections.emptyMap() : om.readValue(detailsS, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("Could not parse IPv6 transition tunnel details.", e);
        }

        return Ipv6TransitionTunnelEntry.create(
                rs.getString("session_key"),
                rs.getString("tunnel_type"),
                rs.getString("transport"),
                details,
                rs.getString("connection_status"),
                rs.getLong("tunneled_bytes"),
                L4MapperTools.fieldsToAddressDataNullablePort("source", rs),
                L4MapperTools.fieldsToAddressDataNullablePort("destination", rs),
                new DateTime(rs.getTimestamp("established_at")),
                rs.getTimestamp("terminated_at") == null ? null : new DateTime(rs.getTimestamp("terminated_at")),
                new DateTime(rs.getTimestamp("most_recent_segment_time")),
                rs.getLong("duration_ms")
        );
    }

}
