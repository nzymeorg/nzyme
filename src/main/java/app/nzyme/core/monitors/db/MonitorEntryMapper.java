package app.nzyme.core.monitors.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MonitorEntryMapper implements RowMapper<MonitorEntry> {

    @Override
    public MonitorEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<UUID> taps;
        Array tapsArray = rs.getArray("taps");
        if (tapsArray != null) {
            taps = Arrays.stream((String[]) tapsArray.getArray())
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
        } else {
            taps = null;
        }

        DateTime statusChangedAt = rs.getTimestamp("status_changed_at") == null ?
                null : new DateTime(rs.getTimestamp("status_changed_at"));

        DateTime last_run = rs.getTimestamp("last_run") == null ?
                null : new DateTime(rs.getTimestamp("last_run"));

        DateTime lastEvent = rs.getTimestamp("last_event") == null ?
                null : new DateTime(rs.getTimestamp("last_event"));

        return MonitorEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getBoolean("enabled"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("description"),
                taps,
                rs.getString("location_id") == null ? null : UUID.fromString(rs.getString("location_id")),
                rs.getInt("trigger_condition"),
                rs.getInt("interval"),
                rs.getInt("lookback"),
                rs.getString("filters"),
                rs.getBoolean("alerted"),
                rs.getString("status"),
                statusChangedAt,
                last_run,
                lastEvent,
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
