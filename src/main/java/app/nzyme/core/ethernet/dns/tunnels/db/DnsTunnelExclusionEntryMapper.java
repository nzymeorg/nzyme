package app.nzyme.core.ethernet.dns.tunnels.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

public class DnsTunnelExclusionEntryMapper implements RowMapper<DnsTunnelExclusionEntry> {

    @Override
    public DnsTunnelExclusionEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String createdByUserId = rs.getString("created_by_user_id");
        Timestamp lastMatchedAt = rs.getTimestamp("last_matched_at");

        return DnsTunnelExclusionEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getString("domain"),
                rs.getString("client_address_cidr"),
                rs.getLong("match_count"),
                lastMatchedAt == null ? null : new DateTime(lastMatchedAt),
                createdByUserId == null ? null : UUID.fromString(createdByUserId),
                rs.getString("created_by_name"),
                rs.getString("created_by_email"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
