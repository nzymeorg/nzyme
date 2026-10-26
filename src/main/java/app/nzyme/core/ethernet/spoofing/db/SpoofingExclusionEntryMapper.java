package app.nzyme.core.ethernet.spoofing.db;

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

public class SpoofingExclusionEntryMapper implements RowMapper<SpoofingExclusionEntry> {

    @Override
    public SpoofingExclusionEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String createdByUserId = rs.getString("created_by_user_id");
        Timestamp lastMatchedAt = rs.getTimestamp("last_matched_at");

        String typesValue = rs.getString("types");
        List<String> types = null;
        if (typesValue != null && !typesValue.isBlank()) {
            types = Arrays.stream(typesValue.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
        }

        return SpoofingExclusionEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                types,
                rs.getString("address_cidr"),
                rs.getString("mac"),
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
