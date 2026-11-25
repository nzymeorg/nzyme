package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ChannelSwitchChannelCountMapper implements RowMapper<ChannelSwitchChannelCount> {

    @Override
    public ChannelSwitchChannelCount map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ChannelSwitchChannelCount.create(
                rs.getInt("new_channel"),
                rs.getObject("new_frequency", Integer.class),
                rs.getLong("frame_count")
        );
    }

}
