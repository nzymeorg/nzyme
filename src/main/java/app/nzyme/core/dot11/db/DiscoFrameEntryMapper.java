package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DiscoFrameEntryMapper implements RowMapper<DiscoFrameEntry> {

    @Override
    public DiscoFrameEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        float signalStrengthAverage = rs.getFloat("signal_strength_average");
        boolean signalStrengthAverageIsNull = rs.wasNull();

        return DiscoFrameEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("tap_uuid")),
                rs.getString("disco_type"),
                rs.getString("transmitter"),
                rs.getString("receiver"),
                rs.getString("bssid"),
                rs.getInt("reason_code"),
                rs.getObject("frequency", Integer.class),
                rs.getObject("signal_strength_min", Integer.class),
                rs.getObject("signal_strength_max", Integer.class),
                signalStrengthAverageIsNull ? null : signalStrengthAverage,
                rs.getLong("frame_count"),
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
