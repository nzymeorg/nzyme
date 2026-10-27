package app.nzyme.core.ethernet.dns.tunnels.db;

import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelJson;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DnsTunnelFindingEntryMapper implements RowMapper<DnsTunnelFindingEntry> {

    @Override
    public DnsTunnelFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DnsTunnelFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("client_address"),
                rs.getString("client_mac"),
                rs.getString("domain"),
                DnsTunnelJson.parseStrings(rs.getString("server_addresses")),
                DnsTunnelJson.parseStrings(rs.getString("indicators")),
                rs.getLong("query_count"),
                rs.getLong("distinct_name_count"),
                rs.getLong("negative_count"),
                rs.getDouble("average_entropy"),
                rs.getDouble("maximum_entropy"),
                rs.getDouble("average_name_length"),
                rs.getInt("maximum_name_length"),
                DnsTunnelJson.parseCounts(rs.getString("record_types")),
                DnsTunnelJson.parseStrings(rs.getString("names")),
                rs.getInt("name_count"),
                DnsTunnelJson.parseUuids(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
