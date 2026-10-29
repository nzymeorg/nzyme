package app.nzyme.core.detection.alerts.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DetectionAlertSearchEntryMapper implements RowMapper<DetectionAlertSearchEntry> {

    private final DetectionAlertEntryMapper alertMapper = new DetectionAlertEntryMapper();

    @Override
    public DetectionAlertSearchEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DetectionAlertSearchEntry.create(
                alertMapper.map(rs, ctx),
                rs.getString("status"),
                rs.getLong("reactivations")
        );
    }

}
