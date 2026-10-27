package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.ArpSpoofingVictimEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingServerEntry;
import app.nzyme.core.ethernet.spoofing.db.DhcpSpoofingVictimEntry;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingFindingEntry;
import app.nzyme.core.ethernet.spoofing.db.NameResolutionSpoofingVictimEntry;
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

import java.sql.Timestamp;
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

    public enum DhcpFindingOrderColumn {

        TYPE("type"),
        SERVER_MAC("server_mac"),
        SERVER_ADDRESS("server_address"),
        RESPONSE_COUNT("response_count"),
        TRANSACTION_COUNT("transaction_count"),
        VICTIM_COUNT("victim_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        DhcpFindingOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public enum DhcpServerOrderColumn {

        MAC("mac"),
        ADDRESS("address"),
        TRUSTED("trusted"),
        RESPONSE_COUNT("response_count"),
        TRANSACTION_COUNT("transaction_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        DhcpServerOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record DhcpVictimRecord(String mac, @Nullable String assignedAddress, long transactionCount,
                                   long leaseCount, DateTime firstSeen, DateTime lastSeen) { }

    public record DhcpActivityCounts(long responseCount, long offerCount, long ackCount, long transactionCount) { }

    public enum NameResolutionOrderColumn {

        RESPONDER_MAC("responder_mac"),
        NAME_COUNT("name_count"),
        RESPONSE_COUNT("response_count"),
        VICTIM_COUNT("victim_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        NameResolutionOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record NameResolutionVictimRecord(String mac, @Nullable String address, List<String> names,
                                             long responseCount, DateTime firstSeen, DateTime lastSeen) { }

    public record NameResolutionResponder(String mac, List<String> addresses, List<String> protocols, long nameCount,
                                          long selfPointingNameCount, long responseCount, long unicastCount,
                                          List<String> namesSample, DateTime firstSeen, DateTime lastSeen) { }

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
                booleanValue(SpoofingRegistryKeys.ARP_ALERT_ON_NEW, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.DHCP_DETECTION_ENABLED, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.DHCP_LEARNING_PERIOD_HOURS, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.DHCP_DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.DHCP_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.DHCP_ALERT_ON_ROGUE_SERVER, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.DHCP_ALERT_ON_PARAMETERS_CHANGED, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.NAMERES_DETECTION_ENABLED, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_DETECTION_WINDOW_MINUTES, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_LLMNR_NAME_THRESHOLD, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_NBTNS_NAME_THRESHOLD, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_MDNS_NAME_THRESHOLD, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_RETENTION_TIME_DAYS, organizationId, tenantId),
                intValue(SpoofingRegistryKeys.NAMERES_RESPONSES_RETENTION_TIME_DAYS, organizationId, tenantId),
                booleanValue(SpoofingRegistryKeys.NAMERES_ALERT_ON_NEW, organizationId, tenantId)
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

    public long countDhcpFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DhcpSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM spoofing_dhcp_findings " +
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

    public List<DhcpSpoofingFindingEntry> findAllDhcpFindings(Filters filters,
                                                              TimeRange timeRange,
                                                              DhcpFindingOrderColumn orderColumn,
                                                              OrderDirection orderDirection,
                                                              int limit,
                                                              int offset,
                                                              UUID organizationId,
                                                              UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DhcpSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_findings " +
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
                        .mapTo(DhcpSpoofingFindingEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> activeDhcpFindingsHistogram(Filters filters,
                                                                          UUID organizationId,
                                                                          UUID tenantId,
                                                                          TimeRange timeRange,
                                                                          Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DhcpSpoofingFindingFilters("f"));

        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COUNT(f.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN spoofing_dhcp_findings f ON f.organization_id = :organization_id " +
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

    public Optional<DhcpSpoofingFindingEntry> findDhcpFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DhcpSpoofingFindingEntry.class)
                        .findOne()
        );
    }

    public Optional<DhcpSpoofingFindingEntry> findDhcpFindingById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM spoofing_dhcp_findings WHERE id = :id")
                .bind("id", id)
                .mapTo(DhcpSpoofingFindingEntry.class)
                .findOne();
    }

    public List<DhcpSpoofingFindingEntry> findAllDhcpFindingsOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM spoofing_dhcp_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(DhcpSpoofingFindingEntry.class)
                .list();
    }

    public List<DhcpSpoofingFindingEntry> findActiveDhcpFindingsOfTenant(Handle handle,
                                                                         UUID organizationId,
                                                                         UUID tenantId,
                                                                         DateTime activeSince) {
        return handle.createQuery("SELECT * FROM spoofing_dhcp_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(DhcpSpoofingFindingEntry.class)
                .list();
    }

    public DhcpSpoofingFindingEntry createDhcpFinding(Handle handle,
                                                      UUID organizationId,
                                                      UUID tenantId,
                                                      DhcpSpoofingFindingType type,
                                                      String serverMac,
                                                      String serverAddress,
                                                      @Nullable String serverIdentifier,
                                                      @Nullable DhcpOfferedParameters offered,
                                                      @Nullable DhcpOfferedParameters expected,
                                                      DateTime firstSeen,
                                                      DateTime lastSeen) {
        return handle.createQuery("INSERT INTO spoofing_dhcp_findings(uuid, organization_id, tenant_id, type, " +
                        "server_mac, server_address, server_identifier, offered, expected, response_count, " +
                        "offer_count, ack_count, transaction_count, victim_count, tap_uuids, first_seen, " +
                        "last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, :type, " +
                        ":server_mac, :server_address::inet, :server_identifier::inet, :offered::jsonb, " +
                        ":expected::jsonb, 0, 0, 0, 0, 0, '[]'::jsonb, :first_seen, :last_seen, NOW(), NOW()) " +
                        "RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("type", type.name())
                .bind("server_mac", serverMac)
                .bind("server_address", serverAddress)
                .bind("server_identifier", serverIdentifier)
                .bind("offered", offered == null ? null : offered.toJson())
                .bind("expected", expected == null ? null : expected.toJson())
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(DhcpSpoofingFindingEntry.class)
                .one();
    }

    public void addDhcpFindingActivity(Handle handle,
                                       long id,
                                       DhcpActivityCounts counts,
                                       @Nullable DhcpOfferedParameters offered,
                                       @Nullable String serverIdentifier,
                                       Collection<UUID> existingTaps,
                                       Collection<UUID> newTaps,
                                       DateTime lastSeen) {
        handle.createUpdate("UPDATE spoofing_dhcp_findings SET response_count = response_count + :response_count, " +
                        "offer_count = offer_count + :offer_count, ack_count = ack_count + :ack_count, " +
                        "transaction_count = transaction_count + :transaction_count, " +
                        "victim_count = (SELECT COUNT(*) FROM spoofing_dhcp_victims WHERE finding_id = :id), " +
                        "offered = COALESCE(:offered::jsonb, offered), " +
                        "server_identifier = COALESCE(:server_identifier::inet, server_identifier), " +
                        "tap_uuids = :tap_uuids::jsonb, last_seen = GREATEST(last_seen, :last_seen), " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("response_count", counts.responseCount())
                .bind("offer_count", counts.offerCount())
                .bind("ack_count", counts.ackCount())
                .bind("transaction_count", counts.transactionCount())
                .bind("offered", offered == null ? null : offered.toJson())
                .bind("server_identifier", serverIdentifier)
                .bind("tap_uuids", tapsJson(existingTaps, newTaps))
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void upsertDhcpVictims(Handle handle, long findingId, Collection<DhcpVictimRecord> victims) {
        if (victims.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO spoofing_dhcp_victims(finding_id, mac, " +
                "assigned_address, transaction_count, lease_count, first_seen, last_seen) VALUES(:finding_id, :mac, " +
                ":assigned_address::inet, :transaction_count, :lease_count, :first_seen, :last_seen) " +
                "ON CONFLICT (finding_id, mac) DO UPDATE SET " +
                "assigned_address = COALESCE(EXCLUDED.assigned_address, spoofing_dhcp_victims.assigned_address), " +
                "transaction_count = spoofing_dhcp_victims.transaction_count + EXCLUDED.transaction_count, " +
                "lease_count = spoofing_dhcp_victims.lease_count + EXCLUDED.lease_count, " +
                "first_seen = LEAST(spoofing_dhcp_victims.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(spoofing_dhcp_victims.last_seen, EXCLUDED.last_seen)");

        for (DhcpVictimRecord v : victims) {
            batch.bind("finding_id", findingId)
                    .bind("mac", v.mac())
                    .bind("assigned_address", v.assignedAddress())
                    .bind("transaction_count", v.transactionCount())
                    .bind("lease_count", v.leaseCount())
                    .bind("first_seen", v.firstSeen())
                    .bind("last_seen", v.lastSeen())
                    .add();
        }

        batch.execute();
    }

    public List<DhcpSpoofingVictimEntry> findDhcpVictims(long findingId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_victims WHERE finding_id = :finding_id " +
                                "ORDER BY lease_count DESC, transaction_count DESC, mac ASC LIMIT :limit")
                        .bind("finding_id", findingId)
                        .bind("limit", limit)
                        .mapTo(DhcpSpoofingVictimEntry.class)
                        .list()
        );
    }

    public void deleteDhcpFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_dhcp_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteDhcpFindingById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM spoofing_dhcp_findings WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public int deleteAllDhcpFindings(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_dhcp_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void retentionCleanDhcp(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM spoofing_dhcp_findings WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("cutoff", cutoff)
                    .execute();

            // Trusted servers are kept so that a returning server does not look rogue. Untrusted ones expire.
            handle.createUpdate("DELETE FROM spoofing_dhcp_servers WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND trusted = false AND last_seen < :cutoff")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("cutoff", cutoff)
                    .execute();
        });
    }

    public long countDhcpServers(Filters filters, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DhcpSpoofingServerFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM spoofing_dhcp_servers " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DhcpSpoofingServerEntry> findAllDhcpServers(Filters filters,
                                                            DhcpServerOrderColumn orderColumn,
                                                            OrderDirection orderDirection,
                                                            int limit,
                                                            int offset,
                                                            UUID organizationId,
                                                            UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DhcpSpoofingServerFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_servers " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(DhcpSpoofingServerEntry.class)
                        .list()
        );
    }

    public List<DhcpSpoofingServerEntry> findAllDhcpServersOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM spoofing_dhcp_servers WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(DhcpSpoofingServerEntry.class)
                .list();
    }

    public Optional<DhcpSpoofingServerEntry> findDhcpServer(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_servers WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DhcpSpoofingServerEntry.class)
                        .findOne()
        );
    }

    public Optional<DhcpSpoofingServerEntry> findDhcpServerByIdentity(String mac,
                                                                      String address,
                                                                      UUID organizationId,
                                                                      UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_dhcp_servers WHERE mac = :mac " +
                                "AND address = :address::inet AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("mac", mac)
                        .bind("address", address)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DhcpSpoofingServerEntry.class)
                        .findOne()
        );
    }

    public Optional<DhcpSpoofingServerEntry> findDhcpServerById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM spoofing_dhcp_servers WHERE id = :id")
                .bind("id", id)
                .mapTo(DhcpSpoofingServerEntry.class)
                .findOne();
    }

    public DhcpSpoofingServerEntry createDhcpServer(Handle handle,
                                                    UUID organizationId,
                                                    UUID tenantId,
                                                    String mac,
                                                    String address,
                                                    @Nullable String serverIdentifier,
                                                    boolean trusted,
                                                    @Nullable String trustSource,
                                                    @Nullable DhcpOfferedParameters baseline,
                                                    DateTime firstSeen,
                                                    DateTime lastSeen) {
        return handle.createQuery("INSERT INTO spoofing_dhcp_servers(uuid, organization_id, tenant_id, mac, " +
                        "address, server_identifier, trusted, trust_source, baseline, baseline_updated_at, latest, " +
                        "response_count, offer_count, ack_count, transaction_count, tap_uuids, first_seen, " +
                        "last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, :mac, " +
                        ":address::inet, :server_identifier::inet, :trusted, :trust_source, :baseline::jsonb, " +
                        "CASE WHEN :baseline::jsonb IS NULL THEN NULL ELSE NOW() END, NULL, 0, 0, 0, 0, " +
                        "'[]'::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("mac", mac)
                .bind("address", address)
                .bind("server_identifier", serverIdentifier)
                .bind("trusted", trusted)
                .bind("trust_source", trustSource)
                .bind("baseline", baseline == null ? null : baseline.toJson())
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(DhcpSpoofingServerEntry.class)
                .one();
    }

    public void addDhcpServerActivity(Handle handle,
                                      long id,
                                      DhcpActivityCounts counts,
                                      @Nullable DhcpOfferedParameters latest,
                                      @Nullable String serverIdentifier,
                                      Collection<UUID> existingTaps,
                                      Collection<UUID> newTaps,
                                      DateTime firstSeen,
                                      DateTime lastSeen) {
        handle.createUpdate("UPDATE spoofing_dhcp_servers SET response_count = response_count + :response_count, " +
                        "offer_count = offer_count + :offer_count, ack_count = ack_count + :ack_count, " +
                        "transaction_count = transaction_count + :transaction_count, " +
                        "latest = COALESCE(:latest::jsonb, latest), " +
                        "server_identifier = COALESCE(:server_identifier::inet, server_identifier), " +
                        "tap_uuids = :tap_uuids::jsonb, first_seen = LEAST(first_seen, :first_seen), " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("response_count", counts.responseCount())
                .bind("offer_count", counts.offerCount())
                .bind("ack_count", counts.ackCount())
                .bind("transaction_count", counts.transactionCount())
                .bind("latest", latest == null ? null : latest.toJson())
                .bind("server_identifier", serverIdentifier)
                .bind("tap_uuids", tapsJson(existingTaps, newTaps))
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void setDhcpServerBaseline(Handle handle, long id, @Nullable DhcpOfferedParameters baseline) {
        handle.createUpdate("UPDATE spoofing_dhcp_servers SET baseline = :baseline::jsonb, " +
                        "baseline_updated_at = NOW(), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("baseline", baseline == null ? null : baseline.toJson())
                .execute();
    }

    // Trusting takes the current configuration as baseline. Untrusting keeps the baseline for reference.
    public void setDhcpServerTrusted(long id, boolean trusted) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE spoofing_dhcp_servers SET trusted = :trusted, trust_source = 'USER', " +
                                "baseline = CASE WHEN :trusted THEN COALESCE(latest, baseline) ELSE baseline END, " +
                                "baseline_updated_at = CASE WHEN :trusted THEN NOW() ELSE baseline_updated_at END, " +
                                "updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("trusted", trusted)
                        .execute()
        );
    }

    public void resetDhcpServerBaselineToLatest(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE spoofing_dhcp_servers SET baseline = COALESCE(latest, baseline), " +
                                "baseline_updated_at = NOW(), updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteDhcpServer(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_dhcp_servers WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public Optional<DateTime> findDhcpProcessedUntil(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT processed_until FROM spoofing_dhcp_detection_state " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> new DateTime(rs.getTimestamp("processed_until")))
                .findOne();
    }

    public void setDhcpProcessedUntil(Handle handle, UUID organizationId, UUID tenantId, DateTime processedUntil) {
        handle.createUpdate("INSERT INTO spoofing_dhcp_detection_state(organization_id, tenant_id, processed_until) " +
                        "VALUES(:organization_id, :tenant_id, :processed_until) " +
                        "ON CONFLICT (organization_id, tenant_id) DO UPDATE SET processed_until = EXCLUDED.processed_until")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("processed_until", processedUntil)
                .execute();
    }

    public Optional<DateTime> findDhcpLearningStartedAt(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT learning_started_at FROM spoofing_dhcp_detection_state " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND learning_started_at IS NOT NULL")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> new DateTime(rs.getTimestamp("learning_started_at")))
                .findOne();
    }

    public Optional<DateTime> findDhcpLearningStartedAt(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> findDhcpLearningStartedAt(handle, organizationId, tenantId));
    }

    public Optional<DateTime> findEarliestDhcpServerFirstSeen(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT MIN(first_seen) AS first_seen FROM spoofing_dhcp_servers " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> {
                            Timestamp ts = rs.getTimestamp("first_seen");
                            return ts == null ? null : new DateTime(ts);
                        })
                        .findOne()
        );
    }

    public Optional<DateTime> findDhcpLearningEndedAt(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT learning_ended_at FROM spoofing_dhcp_detection_state " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND learning_ended_at IS NOT NULL")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> new DateTime(rs.getTimestamp("learning_ended_at")))
                .findOne();
    }

    public Optional<DateTime> findDhcpLearningEndedAt(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> findDhcpLearningEndedAt(handle, organizationId, tenantId));
    }

    // The state row does not exist before the first detection run. The cursor matches the detector's initial one.
    public void endDhcpLearning(UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO spoofing_dhcp_detection_state(organization_id, tenant_id, " +
                                "processed_until, learning_ended_at) " +
                                "VALUES(:organization_id, :tenant_id, NOW() - INTERVAL '24 hours', NOW()) " +
                                "ON CONFLICT (organization_id, tenant_id) DO UPDATE SET learning_ended_at = NOW()")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void restartDhcpLearning(UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO spoofing_dhcp_detection_state(organization_id, tenant_id, " +
                                "processed_until, learning_started_at, learning_ended_at) " +
                                "VALUES(:organization_id, :tenant_id, NOW() - INTERVAL '24 hours', NOW(), NULL) " +
                                "ON CONFLICT (organization_id, tenant_id) DO UPDATE SET learning_started_at = NOW(), " +
                                "learning_ended_at = NULL")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void setDhcpServerLearned(Handle handle, long id) {
        handle.createUpdate("UPDATE spoofing_dhcp_servers SET trusted = true, trust_source = 'LEARNED', " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }


    public long countNameResolutionFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NameResolutionSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM spoofing_nameres_findings " +
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

    public List<NameResolutionSpoofingFindingEntry> findAllNameResolutionFindings(Filters filters,
                                                                                  TimeRange timeRange,
                                                                                  NameResolutionOrderColumn orderColumn,
                                                                                  OrderDirection orderDirection,
                                                                                  int limit,
                                                                                  int offset,
                                                                                  UUID organizationId,
                                                                                  UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NameResolutionSpoofingFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_nameres_findings " +
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
                        .mapTo(NameResolutionSpoofingFindingEntry.class)
                        .list()
        );
    }

    public List<GenericIntegerHistogramEntry> activeNameResolutionFindingsHistogram(Filters filters,
                                                                                    UUID organizationId,
                                                                                    UUID tenantId,
                                                                                    TimeRange timeRange,
                                                                                    Bucketing.BucketingConfiguration bucketing) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new NameResolutionSpoofingFindingFilters("f"));

        String step = "1 " + bucketing.type().getDateTruncName();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT b.bucket AS bucket, COUNT(f.id)::integer AS value " +
                                "FROM generate_series(date_trunc(:date_trunc, :tr_from::timestamptz), " +
                                ":tr_to::timestamptz, :step::interval) AS b(bucket) " +
                                "LEFT JOIN spoofing_nameres_findings f ON f.organization_id = :organization_id " +
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

    public Optional<NameResolutionSpoofingFindingEntry> findNameResolutionFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_nameres_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(NameResolutionSpoofingFindingEntry.class)
                        .findOne()
        );
    }

    public Optional<NameResolutionSpoofingFindingEntry> findNameResolutionFindingById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM spoofing_nameres_findings WHERE id = :id")
                .bind("id", id)
                .mapTo(NameResolutionSpoofingFindingEntry.class)
                .findOne();
    }

    public List<NameResolutionSpoofingFindingEntry> findAllNameResolutionFindingsOfTenant(Handle handle,
                                                                                          UUID organizationId,
                                                                                          UUID tenantId) {
        return handle.createQuery("SELECT * FROM spoofing_nameres_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(NameResolutionSpoofingFindingEntry.class)
                .list();
    }

    public List<NameResolutionSpoofingFindingEntry> findActiveNameResolutionFindingsOfTenant(Handle handle,
                                                                                             UUID organizationId,
                                                                                             UUID tenantId,
                                                                                             DateTime activeSince) {
        return handle.createQuery("SELECT * FROM spoofing_nameres_findings WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(NameResolutionSpoofingFindingEntry.class)
                .list();
    }

    public NameResolutionSpoofingFindingEntry createNameResolutionFinding(Handle handle,
                                                                          UUID organizationId,
                                                                          UUID tenantId,
                                                                          String responderMac,
                                                                          DateTime firstSeen,
                                                                          DateTime lastSeen) {
        return handle.createQuery("INSERT INTO spoofing_nameres_findings(uuid, organization_id, tenant_id, " +
                        "responder_mac, responder_addresses, protocols, names, name_count, response_count, " +
                        "unicast_count, victim_count, tap_uuids, first_seen, last_seen, created_at, updated_at) " +
                        "VALUES(:uuid, :organization_id, :tenant_id, :responder_mac, '[]'::jsonb, '[]'::jsonb, " +
                        "'[]'::jsonb, 0, 0, 0, 0, '[]'::jsonb, :first_seen, :last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("responder_mac", responderMac)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(NameResolutionSpoofingFindingEntry.class)
                .one();
    }

    public void addNameResolutionFindingActivity(Handle handle,
                                                 long id,
                                                 List<String> responderAddresses,
                                                 List<String> protocols,
                                                 List<String> names,
                                                 long responseCount,
                                                 long unicastCount,
                                                 Collection<UUID> existingTaps,
                                                 Collection<UUID> newTaps,
                                                 DateTime lastSeen) {
        handle.createUpdate("UPDATE spoofing_nameres_findings SET responder_addresses = :responder_addresses::jsonb, " +
                        "protocols = :protocols::jsonb, names = :names::jsonb, name_count = :name_count, " +
                        "response_count = response_count + :response_count, " +
                        "unicast_count = unicast_count + :unicast_count, " +
                        "victim_count = (SELECT COUNT(*) FROM spoofing_nameres_victims WHERE finding_id = :id), " +
                        "tap_uuids = :tap_uuids::jsonb, last_seen = GREATEST(last_seen, :last_seen), " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("responder_addresses", NameResolutionJson.toJson(responderAddresses))
                .bind("protocols", NameResolutionJson.toJson(protocols))
                .bind("names", NameResolutionJson.toJson(names))
                .bind("name_count", names.size())
                .bind("response_count", responseCount)
                .bind("unicast_count", unicastCount)
                .bind("tap_uuids", tapsJson(existingTaps, newTaps))
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void upsertNameResolutionVictims(Handle handle, long findingId, List<NameResolutionVictimRecord> victims) {
        if (victims.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("INSERT INTO spoofing_nameres_victims(finding_id, mac, address, " +
                "names, response_count, first_seen, last_seen) VALUES(:finding_id, :mac, :address::inet, " +
                ":names::jsonb, :response_count, :first_seen, :last_seen) " +
                "ON CONFLICT (finding_id, mac) DO UPDATE SET " +
                "address = COALESCE(EXCLUDED.address, spoofing_nameres_victims.address), " +
                "names = (SELECT COALESCE(jsonb_agg(n), '[]'::jsonb) FROM (SELECT DISTINCT n " +
                "FROM jsonb_array_elements(spoofing_nameres_victims.names || EXCLUDED.names) n LIMIT 50) x), " +
                "response_count = spoofing_nameres_victims.response_count + EXCLUDED.response_count, " +
                "first_seen = LEAST(spoofing_nameres_victims.first_seen, EXCLUDED.first_seen), " +
                "last_seen = GREATEST(spoofing_nameres_victims.last_seen, EXCLUDED.last_seen)");

        for (NameResolutionVictimRecord v : victims) {
            batch.bind("finding_id", findingId)
                    .bind("mac", v.mac())
                    .bind("address", v.address())
                    .bind("names", NameResolutionJson.toJson(v.names()))
                    .bind("response_count", v.responseCount())
                    .bind("first_seen", v.firstSeen())
                    .bind("last_seen", v.lastSeen())
                    .add();
        }

        batch.execute();
    }

    public List<NameResolutionSpoofingVictimEntry> findNameResolutionVictims(long findingId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM spoofing_nameres_victims WHERE finding_id = :finding_id " +
                                "ORDER BY response_count DESC, mac ASC LIMIT :limit")
                        .bind("finding_id", findingId)
                        .bind("limit", limit)
                        .mapTo(NameResolutionSpoofingVictimEntry.class)
                        .list()
        );
    }

    public void deleteNameResolutionFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_nameres_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteNameResolutionFindingById(Handle handle, long id) {
        handle.createUpdate("DELETE FROM spoofing_nameres_findings WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public int deleteAllNameResolutionFindings(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_nameres_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void retentionCleanNameResolutionFindings(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM spoofing_nameres_findings WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND last_seen < :cutoff")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }

    public void retentionCleanNameResolutionResponses(List<UUID> taps, DateTime cutoff) {
        if (taps.isEmpty()) {
            return;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM name_resolution_responses WHERE tap_uuid IN (<taps>) " +
                                "AND last_seen < :cutoff")
                        .bindList("taps", taps)
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }

    public long countNameResolutionResponders(List<UUID> taps, TimeRange timeRange) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT responder_mac) FROM name_resolution_responses " +
                                "WHERE tap_uuid IN (<taps>) AND last_seen >= :tr_from AND first_seen <= :tr_to")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }
    
    public List<NameResolutionResponder> findNameResolutionResponders(List<UUID> taps,
                                                                      TimeRange timeRange,
                                                                      int limit,
                                                                      int offset) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT responder_mac, " +
                                "jsonb_agg(DISTINCT host(responder_address)) AS addresses, " +
                                "jsonb_agg(DISTINCT protocol) AS protocols, " +
                                "COUNT(DISTINCT name) AS name_count, " +
                                "COUNT(DISTINCT name) FILTER (WHERE jsonb_exists(answer_addresses, host(responder_address))) " +
                                "AS self_pointing_name_count, " +
                                "SUM(response_count) AS response_count, SUM(unicast_count) AS unicast_count, " +
                                "trim_array(array_agg(DISTINCT name), GREATEST(cardinality(array_agg(DISTINCT name)) - 10, 0)) " +
                                "AS names_sample, " +
                                "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                                "FROM name_resolution_responses WHERE tap_uuid IN (<taps>) " +
                                "AND last_seen >= :tr_from AND first_seen <= :tr_to " +
                                "GROUP BY responder_mac " +
                                "ORDER BY self_pointing_name_count DESC, name_count DESC, response_count DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .map((rs, ctx) -> {
                            java.sql.Array sample = rs.getArray("names_sample");
                            List<String> names = sample == null ? Collections.emptyList()
                                    : Arrays.asList((String[]) sample.getArray());

                            return new NameResolutionResponder(
                                    rs.getString("responder_mac"),
                                    NameResolutionJson.parseStrings(rs.getString("addresses")),
                                    NameResolutionJson.parseStrings(rs.getString("protocols")),
                                    rs.getLong("name_count"),
                                    rs.getLong("self_pointing_name_count"),
                                    rs.getLong("response_count"),
                                    rs.getLong("unicast_count"),
                                    names,
                                    new DateTime(rs.getTimestamp("first_seen")),
                                    new DateTime(rs.getTimestamp("last_seen"))
                            );
                        })
                        .list()
        );
    }

    private static String tapsJson(Collection<UUID> existingTaps, Collection<UUID> newTaps) {
        Set<UUID> taps = Sets.newLinkedHashSet(existingTaps);
        taps.addAll(newTaps);

        try {
            return OM.writeValueAsString(taps.stream().map(UUID::toString).toList());
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize tap UUIDs.", e);
        }
    }

}
