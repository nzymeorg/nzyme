package app.nzyme.core.dashboards.db;

import app.nzyme.core.dashboards.DisplaySettings;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class DisplayEntryMapper implements RowMapper<DisplayEntry> {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Override
    public DisplayEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Timestamp lastSeenAt = rs.getTimestamp("last_seen_at");
        String locationId = rs.getString("location_id");

        List<String> allowedNetworks;
        try {
            allowedNetworks = om.readValue(rs.getString("allowed_networks"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize allowed networks of display.", e);
        }

        return DisplayEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                new DisplaySettings(
                        rs.getString("name"),
                        locationId == null ? null : UUID.fromString(locationId),
                        DisplaySettings.Theme.valueOf(rs.getString("theme")),
                        rs.getString("light_from"),
                        rs.getString("light_until"),
                        rs.getString("time_zone"),
                        DisplaySettings.ClockFormat.valueOf(rs.getString("clock_format")),
                        rs.getInt("scale_percent"),
                        rs.getInt("rotation_seconds"),
                        rs.getInt("refresh_seconds"),
                        allowedNetworks,
                        rs.getBoolean("redacted"),
                        rs.getBoolean("hide_cursor"),
                        expiresAt == null ? null : new DateTime(expiresAt)
                ),
                rs.getString("location_name"),
                lastSeenAt == null ? null : new DateTime(lastSeenAt),
                rs.getString("last_remote_address"),
                rs.getString("last_reported_version"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
