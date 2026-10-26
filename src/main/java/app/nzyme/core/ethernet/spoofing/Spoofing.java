package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingVictimEntry;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

public class Spoofing {

    private static final ObjectMapper OM = new ObjectMapper();

    public enum ArpOrderColumn {

        ADDRESS("address"),
        SPOOFER_MAC("spoofer_mac"),
        OWNER_MAC("owner_mac"),
        CLAIM_COUNT("claim_count"),
        UNSOLICITED_COUNT("unsolicited_count"),
        VICTIM_COUNT("victim_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        ArpOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record VictimRecord(String mac, @Nullable String address, long packetCount, long unsolicitedCount,
                               DateTime firstSeen, DateTime lastSeen) { }

    public record ClaimTimelineBucket(DateTime bucket, String mac, long count) { }

    private final NzymeNode nzyme;
    private final SpoofingExclusions exclusions;

    public Spoofing(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.exclusions = new SpoofingExclusions(nzyme, this);
    }

    public SpoofingExclusions exclusions() {
        return exclusions;
    }

    public SpoofingConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return SpoofingConfiguration.create(
                booleanValue(SpoofingRegistryKeys.ARP_DETECTION_ENABLED, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.ARP_DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.ARP_OWNER_LOOKBACK_HOURS, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.ARP_MINIMUM_CLAIMS, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.ARP_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.ARP_ALERT_ON_NEW, organizationId, tenantId)
        );
    }

    public String stringValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabaseCoreRegistry().getValue(key.key(), organizationId, tenantId)
                .orElse(key.defaultValue().orElseThrow());
    }

    public boolean booleanValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(stringValue(key, organizationId, tenantId));
    }

    public int intValue(RegistryKey key, UUID organizationId, UUID tenantId) {
        return Integer.parseInt(stringValue(key, organizationId, tenantId));
    }

    private static final String TIME_RANGE_CONDITION = "AND first_seen <= :tr_to AND last_seen >= :tr_from ";

    public long countArpFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ArpSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM spoofing_arp_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<ArpSpoofingFindingEntry> findAllArpFindings(Filters filters,
                                                            TimeRange timeRange,
                                                            ArpOrderColumn orderColumn,
                                                            OrderDirection orderDirection,
                                                            int limit,
                                                            int offset,
                                                            UUID organizationId,
                                                            UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ArpSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_arp_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(ArpSpoofingFindingEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> activeArpFindingsHistogram(Filters filters,
                                                                         UUID organizationId,
                                                                         UUID tenantId,
                                                                         TimeRange timeRange,
                                                                         Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new ArpSpoofingFindingFilters("f"));

        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COUNT(f.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN spoofing_arp_findings f ON f.organization_id = :organization_id " +
                                "AND f.tenant_id = :tenant_id AND f.first_seen < b.bucket + :step::interval " +
                                "AND f.last_seen >= b.bucket " + filterFragment.whereSql() +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("step", step)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public Optional<ArpSpoofingFindingEntry> findArpFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_arp_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(ArpSpoofingFindingEntry.class)
                        .findOne()
        );
    }

    public Optional<ArpSpoofingFindingEntry> findArpFindingById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM spoofing_arp_findings WHERE id = :id")
                .bind("id", id)
                .mapTo(ArpSpoofingFindingEntry.class)
                .findOne();
    }

    public List<ArpSpoofingFindingEntry> findAllArpFindingsOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM spoofing_arp_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(ArpSpoofingFindingEntry.class)
                .list();
    }

    public List<ArpSpoofingFindingEntry> findActiveArpFindingsOfTenant(Handle handle,
                                                                       UUID organizationId,
                                                                       UUID tenantId,
                                                                       DateTime activeSince) {
        return handle.createQuery("SELECT * FROM spoofing_arp_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(ArpSpoofingFindingEntry.class)
                .list();
    }

    public ArpSpoofingFindingEntry createArpFinding(Handle handle,
                                                    UUID organizationId,
                                                    UUID tenantId,
                                                    String address,
                                                    String spooferMac,
                                                    String ownerMac,
                                                    DateTime firstSeen,
                                                    DateTime lastSeen) {
        return handle.createQuery("INSERT INTO spoofing_arp_findings(uuid, organization_id, tenant_id, address, " +
                        "spoofer_mac, owner_mac, claim_count, reply_count, unsolicited_count, broadcast_count, " +
                        "owner_claim_count, victim_count, tap_uuids, first_seen, last_seen, created_at, updated_at) " +
                        "VALUES(:uuid, :organization_id, :tenant_id, :address::inet, :spoofer_mac, :owner_mac, " +
                        "0, 0, 0, 0, 0, 0, '[]'::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("address", address)
                .bind("spoofer_mac", spooferMac)
                .bind("owner_mac", ownerMac)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(ArpSpoofingFindingEntry.class)
                .one();
    }

    public void addArpFindingCounts(Handle handle,
                                    long id,
                                    long claimCount,
                                    long replyCount,
                                    long unsolicitedCount,
                                    long broadcastCount,
                                    long ownerClaimCount,
                                    Collection<UUID> existingTaps,
                                    Collection<UUID> newTaps,
                                    DateTime lastSeen) {
        Set<UUID> taps = Sets.newLinkedHashSet(existingTaps);
        taps.addAll(newTaps);

        String tapsJson;
        try {
            tapsJson = OM.writeValueAsString(taps.stream().map(UUID::toString).toList());
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize tap UUIDs.", e);
        }

        handle.createUpdate("UPDATE spoofing_arp_findings SET claim_count = claim_count + :claim_count, " +
                        "reply_count = reply_count + :reply_count, " +
                        "unsolicited_count = unsolicited_count + :unsolicited_count, " +
                        "broadcast_count = broadcast_count + :broadcast_count, " +
                        "owner_claim_count = owner_claim_count + :owner_claim_count, " +
                        "victim_count = (SELECT COUNT(*) FROM spoofing_arp_victims WHERE finding_id = :id), " +
                        "tap_uuids = :tap_uuids::jsonb, last_seen = GREATEST(last_seen, :last_seen), " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("claim_count", claimCount)
                .bind("reply_count", replyCount)
                .bind("unsolicited_count", unsolicitedCount)
                .bind("broadcast_count", broadcastCount)
                .bind("owner_claim_count", ownerClaimCount)
                .bind("tap_uuids", tapsJson)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void upsertArpVictims(Handle handle, long findingId, List<VictimRecord> victims) {
        if (victims.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO spoofing_arp_victims(finding_id, mac, address, " +
                "packet_count, unsolicited_count, first_seen, last_seen) VALUES(:finding_id, :mac, :address::inet, " +
                ":packet_count, :unsolicited_count, :first_seen, :last_seen) " +
                "ON CONFLICT (finding_id, mac) DO UPDATE SET " +
                "address = COALESCE(EXCLUDED.address, spoofing_arp_victims.address), " +
                "packet_count = spoofing_arp_victims.packet_count + EXCLUDED.packet_count, " +
                "unsolicited_count = spoofing_arp_victims.unsolicited_count + EXCLUDED.unsolicited_count, " +
                "first_seen = LEAST(spoofing_arp_victims.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(spoofing_arp_victims.last_seen, EXCLUDED.last_seen)");

        for (VictimRecord v : victims) {
            batch.bind("finding_id", findingId)
                    .bind("mac", v.mac())
                    .bind("address", v.address())
                    .bind("packet_count", v.packetCount())
                    .bind("unsolicited_count", v.unsolicitedCount())
                    .bind("first_seen", v.firstSeen())
                    .bind("last_seen", v.lastSeen())
                    .add();
        }

        batch.execute();
    }

    public List<ArpSpoofingVictimEntry> findArpVictims(long findingId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_arp_victims WHERE finding_id = :finding_id " +
                                "ORDER BY packet_count DESC, mac ASC LIMIT :limit")
                        .bind("finding_id", findingId)
                        .bind("limit", limit)
                        .mapTo(ArpSpoofingVictimEntry.class)
                        .list()
        );
    }

    public List<ClaimTimelineBucket> arpClaimTimeline(ArpSpoofingFindingEntry finding,
                                                      List<UUID> taps,
                                                      DateTime from,
                                                      DateTime to,
                                                      Bucketing.BucketingConfiguration bucketing) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, arp_sender_mac AS mac, " +
                                "COUNT(*) AS count FROM arp_packets WHERE tap_uuid IN (<taps>) " +
                                "AND arp_sender_address = :address::inet AND arp_sender_mac IN (:spoofer_mac, :owner_mac) " +
                                "AND timestamp >= :from AND timestamp <= :to " +
                                "GROUP BY bucket, arp_sender_mac ORDER BY bucket ASC")
                        .bindList("taps", taps)
                        .bind("address", finding.address())
                        .bind("spoofer_mac", finding.spooferMac())
                        .bind("owner_mac", finding.ownerMac())
                        .bind("from", from)
                        .bind("to", to)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .map((rs, ctx) -> new ClaimTimelineBucket(
                                new DateTime(rs.getTimestamp("bucket")),
                                rs.getString("mac"),
                                rs.getLong("count")
                        ))
                        .list()
        );
    }

    public void deleteArpFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_arp_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteArpFindingById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM spoofing_arp_findings WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public int deleteAllArpFindings(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_arp_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void retentionCleanArpFindings(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_arp_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }

}
