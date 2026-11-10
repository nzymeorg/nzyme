package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscoReasonCodeCountMapper implements RowMapper<DiscoReasonCodeCount> {

    @Override
    public DiscoReasonCodeCount map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DiscoReasonCodeCount.create(rs.getInt("reason_code"), rs.getLong("frame_count"));
    }

}
