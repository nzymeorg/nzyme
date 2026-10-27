package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.NameResolutionJson;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DnsSpoofingFindingEntryMapper implements RowMapper<DnsSpoofingFindingEntry> {

    @Override
    public DnsSpoofingFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DnsSpoofingFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("server_address"),
                rs.getString("spoofer_mac"),
                rs.getString("owner_mac"),
                rs.getLong("forged_count"),
                rs.getLong("won_count"),
                rs.getLong("unanswered_count"),
                NameResolutionJson.parseStrings(rs.getString("names")),
                rs.getInt("name_count"),
                NameResolutionJson.parseStrings(rs.getString("answers")),
                rs.getInt("victim_count"),
                SpoofingMapperTools.parseTaps(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
