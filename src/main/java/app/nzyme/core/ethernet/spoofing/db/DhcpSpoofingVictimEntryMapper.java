package app.nzyme.core.ethernet.spoofing.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DhcpSpoofingVictimEntryMapper implements RowMapper<DhcpSpoofingVictimEntry> {

    @Override
    public DhcpSpoofingVictimEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DhcpSpoofingVictimEntry.create(
                rs.getLong("id"),
                rs.getLong("finding_id"),
                rs.getString("mac"),
                rs.getString("assigned_address"),
                rs.getLong("transaction_count"),
                rs.getLong("lease_count"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
