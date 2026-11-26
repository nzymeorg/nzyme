package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConnectedClientDetailsMapper implements RowMapper<ConnectedClientDetails> {

    @Override
    public ConnectedClientDetails map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ConnectedClientDetails.create(
                rs.getString("client_mac"),
                rs.getString("bssid"),
                new DateTime(rs.getTimestamp("last_seen")),
                rs.getObject("signal_strength_average", Double.class)
        );
    }

}
