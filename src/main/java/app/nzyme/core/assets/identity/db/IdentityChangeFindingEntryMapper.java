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
import java.util.List;
import java.util.UUID;

public class IdentityChangeFindingEntryMapper implements RowMapper<IdentityChangeFindingEntry> {

    private static final ObjectMapper OM = JsonMapper.builder().build();

    @Override
    public IdentityChangeFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<IdentityChange> changes;
        try {
            changes = OM.readValue(rs.getString("changes"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize identity change finding.", e);
        }

        return IdentityChangeFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("mac"),
                rs.getInt("score"),
                changes,
                rs.getString("previous_fingerprint"),
                rs.getString("new_fingerprint"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
