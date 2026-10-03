package app.nzyme.core.context.db;

import app.nzyme.core.ethernet.CIDR;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class NetworkContextEntryMapper implements RowMapper<NetworkContextEntry> {

    @Override
    public NetworkContextEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return NetworkContextEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                CIDR.parse(rs.getString("network")),
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
