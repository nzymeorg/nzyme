package app.nzyme.core.ethernet.icmp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IcmpTunnelEntryMapper implements RowMapper<IcmpTunnelEntry> {

    @Override
    public IcmpTunnelEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return IcmpTunnelEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("source_address"),
                rs.getString("source_mac"),
                rs.getString("destination_address"),
                rs.getString("destination_mac"),
                rs.getLong("max_payload_bytes"),
                rs.getLong("packet_count"),
                JsonArrays.uuids(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
