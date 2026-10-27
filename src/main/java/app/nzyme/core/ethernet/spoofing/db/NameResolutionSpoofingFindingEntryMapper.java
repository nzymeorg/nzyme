package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.NameResolutionJson;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class NameResolutionSpoofingFindingEntryMapper implements RowMapper<NameResolutionSpoofingFindingEntry> {

    @Override
    public NameResolutionSpoofingFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return NameResolutionSpoofingFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("responder_mac"),
                NameResolutionJson.parseStrings(rs.getString("responder_addresses")),
                NameResolutionJson.parseStrings(rs.getString("protocols")),
                NameResolutionJson.parseStrings(rs.getString("names")),
                rs.getInt("name_count"),
                rs.getLong("response_count"),
                rs.getLong("unicast_count"),
                rs.getInt("victim_count"),
                SpoofingMapperTools.parseTaps(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
