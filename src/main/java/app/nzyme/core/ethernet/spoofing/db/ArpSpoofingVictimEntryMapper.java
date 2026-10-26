package app.nzyme.core.ethernet.spoofing.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArpSpoofingVictimEntryMapper implements RowMapper<ArpSpoofingVictimEntry> {

    @Override
    public ArpSpoofingVictimEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ArpSpoofingVictimEntry.create(
                rs.getLong("id"),
                rs.getLong("finding_id"),
                rs.getString("mac"),
                rs.getString("address"),
                rs.getLong("packet_count"),
                rs.getLong("unsolicited_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
