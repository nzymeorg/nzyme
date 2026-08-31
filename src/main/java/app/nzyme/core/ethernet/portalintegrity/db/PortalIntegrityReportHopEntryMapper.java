package app.nzyme.core.ethernet.portalintegrity.db;

import app.nzyme.core.ethernet.L4MapperTools;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PortalIntegrityReportHopEntryMapper implements RowMapper<PortalIntegrityReportHopEntry> {

    @Override
    public PortalIntegrityReportHopEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        byte[] rawBytes = rs.getBytes("raw");
        String raw = rawBytes == null ? null : new String(rawBytes, StandardCharsets.UTF_8);

        return PortalIntegrityReportHopEntry.create(
                rs.getInt("hop_index"),
                rs.getString("url"),
                L4MapperTools.fieldsToAddressDataNoMacNoPort("resolved", rs),
                rs.getInt("status"),
                rs.getString("followed_to"),
                rs.getString("Completeness"),
                raw,
                rs.getString("body_sha256"),
                rs.getString("tls")
        );
    }

}
