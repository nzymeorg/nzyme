package app.nzyme.core.ethernet.icmp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IcmpTracerouteEntryMapper implements RowMapper<IcmpTracerouteEntry> {

    @Override
    public IcmpTracerouteEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        int protocol = rs.getInt("protocol");
        boolean protocolNull = rs.wasNull();

        return IcmpTracerouteEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("source_address"),
                rs.getString("source_mac"),
                rs.getString("destination_address"),
                protocolNull ? null : protocol,
                JsonArrays.strings(rs.getString("routers")),
                rs.getLong("packet_count"),
                rs.getBoolean("destination_reached"),
                JsonArrays.uuids(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
