package app.nzyme.core.ethernet.spoofing.db;

import com.google.common.collect.Lists;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class ArpSpoofingFindingEntryMapper implements RowMapper<ArpSpoofingFindingEntry> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public ArpSpoofingFindingEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        List<UUID> taps = Lists.newArrayList();
        String tapsJson = rs.getString("tap_uuids");
        if (tapsJson != null && !tapsJson.isBlank()) {
            try {
                List<String> parsed = OM.readValue(tapsJson, new TypeReference<>() {});
                for (String t : parsed) {
                    taps.add(UUID.fromString(t));
                }
            } catch (Exception e) {
                throw new SQLException("Could not parse tap UUIDs of ARP spoofing finding.", e);
            }
        }

        return ArpSpoofingFindingEntry.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("address"),
                rs.getString("spoofer_mac"),
                rs.getString("owner_mac"),
                rs.getLong("claim_count"),
                rs.getLong("reply_count"),
                rs.getLong("unsolicited_count"),
                rs.getLong("broadcast_count"),
                rs.getLong("owner_claim_count"),
                rs.getInt("victim_count"),
                taps,
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
