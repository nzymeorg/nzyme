package app.nzyme.core.ethernet.nat.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NATTraversalDiscoveryHistogramBucketMapper implements RowMapper<NATTraversalDiscoveryHistogramBucket> {

    @Override
    public NATTraversalDiscoveryHistogramBucket map(ResultSet rs, StatementContext ctx) throws SQLException {
        return NATTraversalDiscoveryHistogramBucket.create(
                rs.getLong("complete_count"),
                rs.getLong("incomplete_count"),
                rs.getLong("error_count"),
                new DateTime(rs.getTimestamp("bucket"))
        );
    }

}
