package app.nzyme.core.dot11.db;

import com.google.common.collect.Lists;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class SSIDChannelDetailsMapper implements RowMapper<SSIDChannelDetails> {

    @Override
    public SSIDChannelDetails map(ResultSet rs, StatementContext ctx) throws SQLException {
        // The is_wps flag is stored as String for database simplicity reasons and we have to cast here.
        List<Boolean> hasWps = Lists.newArrayList();
        hasWps.addAll(Arrays.asList(((Boolean[]) rs.getArray("has_wps").getArray())));

        int frequency = rs.getInt("frequency");
        boolean hasFrequency = !rs.wasNull();

        return SSIDChannelDetails.create(
                rs.getString("ssid"),
                Lists.newArrayList((String[]) rs.getArray("security_protocols").getArray()),
                hasWps,
                Lists.newArrayList((String[]) rs.getArray("infrastructure_types").getArray()),
                rs.getFloat("signal_strength_average"),
                hasFrequency ? frequency : null,
                rs.getLong("total_frames"),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
