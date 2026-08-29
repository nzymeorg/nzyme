package app.nzyme.core.ethernet.rtsp.db;

import app.nzyme.core.ethernet.L4MapperTools;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import com.google.common.collect.Sets;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class RTSPStreamEntryMapper implements RowMapper<RTSPStreamEntry> {

    @Override
    public RTSPStreamEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        DateTime setupTerminatedAt = rs.getTimestamp("setup_terminated_at") == null ?
                null : new DateTime(rs.getTimestamp("setup_terminated_at"));

        DateTime streamMostRecentSegmentTime = rs.getTimestamp("stream_most_recent_segment_time") == null ?
                null : new DateTime(rs.getTimestamp("stream_most_recent_segment_time"));

        DateTime lastActivity = rs.getTimestamp("last_activity") == null ?
                null : new DateTime(rs.getTimestamp("last_activity"));

        // Guard against NULL attributes if we have no underlying connection JOINed.
        L4AddressData setupSourceAddress = rs.getString("setup_source_address") == null
                ? null : L4MapperTools.fieldsToAddressData("setup_source", rs);
        L4AddressData setupDestinationAddress = rs.getString("setup_destination_address") == null
                ? null : L4MapperTools.fieldsToAddressData("setup_destination", rs);
        L4AddressData streamSourceAddress = rs.getString("stream_source_address") == null
                ? null : L4MapperTools.fieldsToAddressData("stream_source", rs);
        L4AddressData streamDestinationAddress = rs.getString("stream_destination_address") == null
                ? null : L4MapperTools.fieldsToAddressData("stream_destination", rs);

        return RTSPStreamEntry.create(
                rs.getString("setup_tcp_session_key"),
                rs.getString("state"),
                rs.getString("media_locator"),
                rs.getString("request_uri"),
                rs.getString("client_agent"),
                rs.getString("server_info"),
                rs.getString("authentication"),
                Sets.newHashSet((String[]) rs.getArray("flags").getArray()),
                rs.getString("setup_connection_status"),
                new DateTime(rs.getTimestamp("setup_established_at")),
                setupTerminatedAt,
                new DateTime(rs.getTimestamp("setup_most_recent_segment_time")),
                lastActivity,
                rs.getBoolean("is_active"),
                rs.getLong("duration_ms"),
                setupSourceAddress,
                setupDestinationAddress,
                rs.getLong("setup_bytes_exchanged"),
                rs.getString("stream_l4_type"),
                streamMostRecentSegmentTime,
                streamSourceAddress,
                streamDestinationAddress,
                rs.getLong("stream_bytes_rx"),
                rs.getLong("stream_bytes_tx")
        );
    }

}
