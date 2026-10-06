package app.nzyme.core.security.authentication.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ApiKeyEntryMapper implements RowMapper<ApiKeyEntry> {

    @Override
    public ApiKeyEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        DateTime lastActivity = rs.getTimestamp("last_activity") == null ? null
                : new DateTime(rs.getTimestamp("last_activity"));

        DateTime expiresAt = rs.getTimestamp("expires_at") == null ? null
                : new DateTime(rs.getTimestamp("expires_at"));

        return ApiKeyEntry.create(
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("name"),
                rs.getString("key"),
                lastActivity,
                expiresAt,
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
