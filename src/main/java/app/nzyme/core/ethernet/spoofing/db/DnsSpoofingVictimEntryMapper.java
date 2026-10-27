package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.NameResolutionJson;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DnsSpoofingVictimEntryMapper implements RowMapper<DnsSpoofingVictimEntry> {

    @Override
    public DnsSpoofingVictimEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DnsSpoofingVictimEntry.create(
                rs.getLong("id"),
                rs.getLong("finding_id"),
                rs.getString("mac"),
                rs.getString("address"),
                NameResolutionJson.parseStrings(rs.getString("names")),
                rs.getLong("forged_count"),
                rs.getLong("won_count"),
                rs.getLong("unanswered_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
