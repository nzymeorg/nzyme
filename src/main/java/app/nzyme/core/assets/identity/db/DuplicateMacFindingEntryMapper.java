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
import java.util.stream.Collectors;

public class DuplicateMacFindingEntryMapper implements RowMapper<DuplicateMacFindingEntry> {

    private static final ObjectMapper OM = JsonMapper.builder().build();

    @Override
    public DuplicateMacFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<DuplicateMacStack> stacks;
        List<String> taps;
        try {
            stacks = OM.readValue(rs.getString("stacks"), new TypeReference<>() {});
            taps = OM.readValue(rs.getString("tap_uuids"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize duplicate MAC finding.", e);
        }

        return DuplicateMacFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("mac"),
                stacks,
                rs.getInt("stack_count"),
                rs.getLong("session_count"),
                taps.stream().map(UUID::fromString).collect(Collectors.toList()),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
