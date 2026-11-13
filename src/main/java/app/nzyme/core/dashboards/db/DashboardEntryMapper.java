package app.nzyme.core.dashboards.db;

import app.nzyme.core.dashboards.DashboardSettings;
import app.nzyme.core.dashboards.DashboardWidget;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DashboardEntryMapper implements RowMapper<DashboardEntry> {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Override
    public DashboardEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<UUID> scopeIds;
        List<DashboardWidget> widgets;
        try {
            scopeIds = om.readValue(rs.getString("scope_ids"), new TypeReference<>() {});
            widgets = om.readValue(rs.getString("widgets"), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException("Could not deserialize scope or widgets of dashboard.", e);
        }

        return DashboardEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                new DashboardSettings(
                        rs.getString("name"),
                        rs.getString("description"),
                        DashboardSettings.Scope.valueOf(rs.getString("scope_type")),
                        scopeIds,
                        rs.getInt("window_minutes"),
                        widgets
                ),
                rs.getLong("display_count"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
