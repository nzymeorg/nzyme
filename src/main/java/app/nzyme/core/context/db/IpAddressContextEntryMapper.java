package app.nzyme.core.context.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IpAddressContextEntryMapper implements RowMapper<IpAddressContextEntry> {

    @Override
    public IpAddressContextEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return IpAddressContextEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                InetAddress.ofLiteral(rs.getString("ip_address")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("notes"),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
