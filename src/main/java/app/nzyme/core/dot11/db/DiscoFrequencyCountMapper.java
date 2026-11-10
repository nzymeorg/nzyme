package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscoFrequencyCountMapper implements RowMapper<DiscoFrequencyCount> {

    @Override
    public DiscoFrequencyCount map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DiscoFrequencyCount.create(rs.getObject("frequency", Integer.class), rs.getLong("frame_count"));
    }

}
