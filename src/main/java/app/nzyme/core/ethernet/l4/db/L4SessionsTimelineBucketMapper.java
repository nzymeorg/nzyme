package app.nzyme.core.ethernet.l4.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class L4SessionsTimelineBucketMapper implements RowMapper<L4SessionsTimelineBucket> {

    @Override
    public L4SessionsTimelineBucket map(ResultSet rs, StatementContext ctx) throws SQLException {
        return L4SessionsTimelineBucket.create(
                new DateTime(rs.getTimestamp("bucket")),
                rs.getLong("sessions"),
                rs.getLong("bytes")
        );
    }

}
