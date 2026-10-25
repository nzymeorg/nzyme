package app.nzyme.core.ethernet.icmp.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IcmpPacketEntryMapper implements RowMapper<IcmpPacketEntry> {

    @Override
    public IcmpPacketEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return IcmpPacketEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("tap_uuid")),
                rs.getString("source_mac"),
                rs.getString("destination_mac"),
                rs.getString("source_address"),
                rs.getString("destination_address"),
                rs.getInt("icmp_type"),
                rs.getInt("icmp_code"),
                rs.getLong("packet_count"),
                rs.getInt("bytes_min"),
                rs.getInt("bytes_max"),
                rs.getInt("ip_ttl"),
                rs.getInt("ip_tos"),
                rs.getBoolean("ip_df"),
                rs.getString("redirect_gateway"),
                nullableInt(rs, "quoted_protocol"),
                rs.getString("quoted_source_address"),
                rs.getString("quoted_destination_address"),
                nullableInt(rs, "quoted_source_port"),
                nullableInt(rs, "quoted_destination_port"),
                nullableInt(rs, "quoted_ttl"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

}
