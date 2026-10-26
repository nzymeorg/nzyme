package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import app.nzyme.core.ethernet.spoofing.DhcpSpoofingFindingType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DhcpSpoofingFindingEntryMapper implements RowMapper<DhcpSpoofingFindingEntry> {

    @Override
    public DhcpSpoofingFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DhcpSpoofingFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                DhcpSpoofingFindingType.valueOf(rs.getString("type")),
                rs.getString("server_mac"),
                rs.getString("server_address"),
                rs.getString("server_identifier"),
                DhcpOfferedParameters.fromJson(rs.getString("offered")),
                DhcpOfferedParameters.fromJson(rs.getString("expected")),
                rs.getLong("response_count"),
                rs.getLong("offer_count"),
                rs.getLong("ack_count"),
                rs.getLong("transaction_count"),
                rs.getInt("victim_count"),
                SpoofingMapperTools.parseTaps(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
