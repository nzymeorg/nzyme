package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.NameResolutionJson;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NameResolutionSpoofingVictimEntryMapper implements RowMapper<NameResolutionSpoofingVictimEntry> {

    @Override
    public NameResolutionSpoofingVictimEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return NameResolutionSpoofingVictimEntry.create(
                rs.getLong("id"),
                rs.getLong("finding_id"),
                rs.getString("mac"),
                rs.getString("address"),
                NameResolutionJson.parseStrings(rs.getString("names")),
                rs.getLong("response_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
