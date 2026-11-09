package app.nzyme.core.database.maintenance.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

public class MaintenanceLogEntryMapper implements RowMapper<MaintenanceLogEntry> {

    @Override
    public MaintenanceLogEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        Boolean success = rs.getObject("success") == null ? null : rs.getBoolean("success");

        return MaintenanceLogEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("node_id")),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("initiator"),
                rs.getString("details"),
                success,
                new DateTime(rs.getTimestamp("started_at")),
                finishedAt == null ? null : new DateTime(finishedAt)
        );
    }

}
