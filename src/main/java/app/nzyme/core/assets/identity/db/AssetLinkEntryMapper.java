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

public class AssetLinkEntryMapper implements RowMapper<AssetLinkEntry> {

    private static final ObjectMapper OM = JsonMapper.builder().build();

    @Override
    public AssetLinkEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<AssetLinkEvidence> evidence;
        try {
            evidence = OM.readValue(rs.getString("evidence"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize asset link evidence.", e);
        }

        return AssetLinkEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("predecessor_mac"),
                rs.getString("successor_mac"),
                rs.getString("kind"),
                rs.getInt("confidence"),
                evidence,
                rs.getString("status"),
                new DateTime(rs.getTimestamp("predecessor_last_seen")),
                new DateTime(rs.getTimestamp("successor_first_seen")),
                new DateTime(rs.getTimestamp("detected_at")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
