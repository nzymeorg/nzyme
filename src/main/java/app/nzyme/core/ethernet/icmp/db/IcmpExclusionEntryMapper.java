package app.nzyme.core.ethernet.icmp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class IcmpExclusionEntryMapper implements RowMapper<IcmpExclusionEntry> {

    @Override
    public IcmpExclusionEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String createdByUserId = rs.getString("created_by_user_id");
        Timestamp lastMatchedAt = rs.getTimestamp("last_matched_at");

        String types = rs.getString("detection_types");
        List<String> detectionTypes = null;
        if (types != null && !types.isBlank()) {
            detectionTypes = Arrays.stream(types.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
        }

        return IcmpExclusionEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                detectionTypes,
                rs.getString("source_cidr"),
                rs.getString("source_mac"),
                rs.getString("gateway_cidr"),
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
