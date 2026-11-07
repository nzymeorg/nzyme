package app.nzyme.core.assets.identity.db;

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

public class AssetIdentityExclusionEntryMapper implements RowMapper<AssetIdentityExclusionEntry> {

    private static final ObjectMapper OM = JsonMapper.builder().build();

    @Override
    public AssetIdentityExclusionEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String createdByUserId = rs.getString("created_by_user_id");
        Timestamp lastMatchedAt = rs.getTimestamp("last_matched_at");

        List<String> types;
        try {
            types = OM.readValue(rs.getString("types"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize asset identity exclusion types.", e);
        }

        return AssetIdentityExclusionEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                types,
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
