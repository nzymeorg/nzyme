package app.nzyme.core.detection.alerts.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DetectionAlertEntryMapper implements RowMapper<DetectionAlertEntry> {

    @Override
    public DetectionAlertEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        UUID dot11MonitoredNetworkId = rs.getString("dot11_monitored_network_id") == null ?
                null : UUID.fromString(rs.getString("dot11_monitored_network_id"));
        UUID tapId = rs.getString("tap_id") == null ?
                null : UUID.fromString(rs.getString("tap_id"));
        UUID locationId = rs.getString("location_id") == null ?
                null : UUID.fromString(rs.getString("location_id"));
        UUID organizationId = rs.getString("organization_id") == null ?
                null : UUID.fromString(rs.getString("organization_id"));
        UUID tenantId = rs.getString("tenant_id") == null ?
                null : UUID.fromString(rs.getString("tenant_id"));

        return DetectionAlertEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getBoolean("is_resolved"),
                dot11MonitoredNetworkId,
                tapId,
                locationId,
                rs.getString("detection_type"),
                rs.getString("subsystem"),
                rs.getString("details"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("last_seen")),
                rs.getString("comparison_checksum"),
                organizationId,
                tenantId
        );
    }

}
