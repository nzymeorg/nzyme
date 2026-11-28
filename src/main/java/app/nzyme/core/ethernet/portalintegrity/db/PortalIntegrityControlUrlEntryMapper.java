package app.nzyme.core.ethernet.portalintegrity.db;

import app.nzyme.core.ethernet.portalintegrity.PortalExpectation;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class PortalIntegrityControlUrlEntryMapper implements RowMapper<PortalIntegrityControlUrlEntry> {

    @Override
    public PortalIntegrityControlUrlEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        String lastReportUuid = rs.getString("last_report_uuid");
        Timestamp inTapConfigurationSince = rs.getTimestamp("in_tap_configuration_since");

        return PortalIntegrityControlUrlEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                UUID.fromString(rs.getString("tap_uuid")),
                rs.getString("probe_name"),
                rs.getString("control_url"),
                rs.getInt("ip_version"),
                PortalExpectation.valueOf(rs.getString("expectation")),
                List.of((String[]) rs.getArray("expected_final_urls").getArray()),
                List.of((String[]) rs.getArray("expected_addresses").getArray()),
                List.of((String[]) rs.getArray("expected_certificates").getArray()),
                lastReportUuid == null ? null : UUID.fromString(lastReportUuid),
                new DateTime(rs.getTimestamp("last_report_at")),
                rs.getObject("report_interval_seconds", Integer.class),
                rs.getString("last_error"),
                rs.getInt("last_hop_count"),
                rs.getObject("last_portal_presented", Boolean.class),
                rs.getString("last_final_url"),
                rs.getString("last_final_address"),
                rs.getString("last_final_certificate"),
                inTapConfigurationSince == null ? null : new DateTime(inTapConfigurationSince),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
