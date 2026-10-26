package app.nzyme.core.ethernet.spoofing.db;

import app.nzyme.core.ethernet.spoofing.DhcpOfferedParameters;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class DhcpSpoofingServerEntryMapper implements RowMapper<DhcpSpoofingServerEntry> {

    @Override
    public DhcpSpoofingServerEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        Timestamp baselineUpdatedAt = rs.getTimestamp("baseline_updated_at");

        return DhcpSpoofingServerEntry.create(
                rs.getLong("id"),
                java.util.UUID.fromString(rs.getString("uuid")),
                java.util.UUID.fromString(rs.getString("organization_id")),
                java.util.UUID.fromString(rs.getString("tenant_id")),
                rs.getString("mac"),
                rs.getString("address"),
                rs.getString("server_identifier"),
                rs.getBoolean("trusted"),
                rs.getString("trust_source"),
                DhcpOfferedParameters.fromJson(rs.getString("baseline")),
                baselineUpdatedAt == null ? null : new DateTime(baselineUpdatedAt),
                DhcpOfferedParameters.fromJson(rs.getString("latest")),
                rs.getLong("response_count"),
                rs.getLong("offer_count"),
                rs.getLong("ack_count"),
                rs.getLong("transaction_count"),
                SpoofingMapperTools.parseTaps(rs.getString("tap_uuids")),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
