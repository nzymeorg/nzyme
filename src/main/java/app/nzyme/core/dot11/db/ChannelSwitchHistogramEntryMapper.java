package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ChannelSwitchHistogramEntryMapper implements RowMapper<ChannelSwitchHistogramEntry> {

    @Override
    public ChannelSwitchHistogramEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ChannelSwitchHistogramEntry.create(
                new DateTime(rs.getTimestamp("bucket")),
                rs.getLong("frame_count"),
                rs.getLong("anomalous_frame_count")
        );
    }

}
