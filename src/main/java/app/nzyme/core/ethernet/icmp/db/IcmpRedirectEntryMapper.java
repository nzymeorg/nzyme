package app.nzyme.core.ethernet.icmp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IcmpRedirectEntryMapper implements RowMapper<IcmpRedirectEntry> {

    @Override
    public IcmpRedirectEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return IcmpRedirectEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("sender_address"),
                rs.getString("sender_mac"),
                rs.getString("gateway_address"),
                rs.getString("target_address"),
                rs.getString("target_mac"),
                JsonArrays.strings(rs.getString("redirected_destinations")),
                rs.getLong("packet_count"),
                JsonArrays.uuids(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
