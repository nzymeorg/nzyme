package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscoHistogramEntryMapper implements RowMapper<DiscoHistogramEntry> {

    @Override
    public DiscoHistogramEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DiscoHistogramEntry.create(
                new DateTime(rs.getTimestamp("bucket")),
                rs.getLong("frame_count"),
                rs.getLong("deauthentication_count"),
                rs.getLong("disassociation_count"),
                rs.getLong("broadcast_count")
        );
    }

}
