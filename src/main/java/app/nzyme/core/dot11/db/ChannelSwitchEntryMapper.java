package app.nzyme.core.dot11.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

public class ChannelSwitchEntryMapper implements RowMapper<ChannelSwitchEntry> {

    @Override
    public ChannelSwitchEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        float signalStrengthAverage = rs.getFloat("signal_strength_average");
        boolean signalStrengthAverageIsNull = rs.wasNull();

        return ChannelSwitchEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("tap_uuid")),
                rs.getString("bssid"),
                rs.getString("frame_type"),
                rs.getString("announcement_type"),
                rs.getInt("switch_mode"),
                rs.getObject("frequency", Integer.class),
                rs.getObject("new_operating_class", Integer.class),
                rs.getInt("new_channel"),
                rs.getObject("new_frequency", Integer.class),
                rs.getInt("count_min"),
                rs.getInt("count_max"),
                rs.getLong("frame_count"),
                rs.getInt("peak_frames_per_second"),
                Arrays.asList((String[]) rs.getArray("anomalies").getArray()),
                rs.getString("outcome"),
                rs.getObject("signal_strength_min", Integer.class),
                rs.getObject("signal_strength_max", Integer.class),
                signalStrengthAverageIsNull ? null : signalStrengthAverage,
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
