package app.nzyme.core.ethernet.portalintegrity.db;

import com.google.common.collect.Lists;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PortalIntegrityReportEntryMapper implements RowMapper<PortalIntegrityReportEntry> {

    @Override
    public PortalIntegrityReportEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return PortalIntegrityReportEntry.create(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("control_url"),
                rs.getString("probe_interface"),
                rs.getString("probe_mac"),
                rs.getString("probe_name"),
                rs.getString("assigned_address"),
                rs.getString("gateway_address"),
                rs.getString("dhcp_server_address"),
                Lists.newArrayList((String[]) rs.getArray("dns_servers").getArray()),
                rs.getInt("hop_count"),
                rs.getString("last_hop_url"),
                rs.getString("error"),
                new DateTime(rs.getTimestamp("probed_at"))
        );
    }

}
