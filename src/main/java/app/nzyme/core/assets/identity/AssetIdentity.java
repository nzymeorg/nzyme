package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.identity.db.*;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AssetIdentity {

    private final NzymeNode nzyme;
    private final AssetIdentityExclusions exclusions;
    private final ObjectMapper om = JsonMapper.builder().build();

    public static final String LINK_KIND_SUCCESSOR = "SUCCESSOR";
    public static final String LINK_KIND_ALTERNATE = "ALTERNATE";

    public static final String LINK_STATUS_OPEN = "OPEN";
    public static final String LINK_STATUS_CONFIRMED = "CONFIRMED";
    public static final String LINK_STATUS_DISMISSED = "DISMISSED";

    public AssetIdentity(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.exclusions = new AssetIdentityExclusions(nzyme);
    }

    public AssetIdentityExclusions exclusions() {
        return exclusions;
    }

    public AssetIdentityConfiguration getConfiguration(UUID organizationId, UUID tenantId) {
        return AssetIdentityConfiguration.create(
                booleanValue(AssetIdentityRegistryKeys.DUPLICATE_DETECTION_ENABLED, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.DUPLICATE_WINDOW_MINUTES, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.DUPLICATE_MINIMUM_SESSIONS, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.DUPLICATE_REQUIRE_STRONG_DIFFERENCE, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.DUPLICATE_ALERT_ON_NEW, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.CHANGE_DETECTION_ENABLED, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.CHANGE_WINDOW_HOURS, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.CHANGE_MINIMUM_ASSET_AGE_HOURS, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.CHANGE_SCORE_THRESHOLD, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.CHANGE_ALERT_ON_NEW, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.SUCCESSOR_DETECTION_ENABLED, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.SUCCESSOR_LOOKBACK_HOURS, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.SUCCESSOR_QUIET_MINUTES, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.SUCCESSOR_MINIMUM_CONFIDENCE, organizationId, tenantId),
                booleanValue(AssetIdentityRegistryKeys.SUCCESSOR_ALERT_ON_NEW, organizationId, tenantId),
                intValue(AssetIdentityRegistryKeys.RETENTION_TIME_DAYS, organizationId, tenantId)
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

    public String toJson(Object o) {
        return om.writeValueAsString(o);
    }

    public enum DuplicateOrderColumn {
        MAC("mac"),
        STACK_COUNT("stack_count"),
        SESSION_COUNT("session_count"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        DuplicateOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private static final String TIME_RANGE_CONDITION = "AND first_seen <= :tr_to AND last_seen >= :tr_from ";

    public long countDuplicateFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DuplicateMacFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_identity_duplicate_findings " +
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

    public List<DuplicateMacFindingEntry> findAllDuplicateFindings(Filters filters,
                                                                   TimeRange timeRange,
                                                                   DuplicateOrderColumn orderColumn,
                                                                   OrderDirection orderDirection,
                                                                   int limit,
                                                                   int offset,
                                                                   UUID organizationId,
                                                                   UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DuplicateMacFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_duplicate_findings " +
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
                        .mapTo(DuplicateMacFindingEntry.class)
                        .list()
        );
    }

    public Optional<DuplicateMacFindingEntry> findDuplicateFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_duplicate_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(DuplicateMacFindingEntry.class)
                        .findOne()
        );
    }

    public Optional<DuplicateMacFindingEntry> findDuplicateFindingById(Handle handle, long id) {
        return handle.createQuery("SELECT * FROM assets_identity_duplicate_findings WHERE id = :id")
                .bind("id", id)
                .mapTo(DuplicateMacFindingEntry.class)
                .findOne();
    }

    public List<DuplicateMacFindingEntry> findActiveDuplicateFindingsOfTenant(Handle handle,
                                                                              UUID organizationId,
                                                                              UUID tenantId,
                                                                              DateTime activeSince) {
        return handle.createQuery("SELECT * FROM assets_identity_duplicate_findings " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(DuplicateMacFindingEntry.class)
                .list();
    }

    public long countDuplicateFindingsOfMac(String mac, UUID organizationId, UUID tenantId, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_identity_duplicate_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac = :mac AND last_seen >= :since")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", since)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DuplicateMacFindingEntry> findDuplicateFindingsOfMac(String mac, UUID organizationId, UUID tenantId,
                                                                     DateTime since, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_duplicate_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac = :mac AND last_seen >= :since ORDER BY last_seen DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", since)
                        .bind("limit", limit)
                        .mapTo(DuplicateMacFindingEntry.class)
                        .list()
        );
    }

    public DuplicateMacFindingEntry createDuplicateFinding(Handle handle,
                                                           UUID organizationId,
                                                           UUID tenantId,
                                                           String mac,
                                                           List<DuplicateMacStack> stacks,
                                                           int stackCount,
                                                           long sessionCount,
                                                           List<UUID> taps,
                                                           DateTime firstSeen,
                                                           DateTime lastSeen) {
        return handle.createQuery("INSERT INTO assets_identity_duplicate_findings(uuid, organization_id, " +
                        "tenant_id, mac, stacks, stack_count, session_count, tap_uuids, first_seen, last_seen, " +
                        "created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, :mac, :stacks::jsonb, " +
                        ":stack_count, :session_count, :tap_uuids::jsonb, :first_seen, :last_seen, NOW(), NOW()) " +
                        "RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("mac", mac)
                .bind("stacks", toJson(stacks))
                .bind("stack_count", stackCount)
                .bind("session_count", sessionCount)
                .bind("tap_uuids", toJson(taps))
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(DuplicateMacFindingEntry.class)
                .one();
    }

    public void updateDuplicateFinding(Handle handle,
                                       long id,
                                       List<DuplicateMacStack> stacks,
                                       int stackCount,
                                       long sessionCount,
                                       List<UUID> taps,
                                       DateTime lastSeen) {
        handle.createUpdate("UPDATE assets_identity_duplicate_findings SET stacks = :stacks::jsonb, " +
                        "stack_count = :stack_count, session_count = :session_count, tap_uuids = :tap_uuids::jsonb, " +
                        "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("stacks", toJson(stacks))
                .bind("stack_count", stackCount)
                .bind("session_count", sessionCount)
                .bind("tap_uuids", toJson(taps))
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void deleteDuplicateFinding(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_duplicate_findings WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteDuplicateFindings(List<UUID> uuids, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_duplicate_findings WHERE uuid IN (<uuids>) " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteDuplicateFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DuplicateMacFindingFilters());

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_duplicate_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

    public enum ChangeOrderColumn {
        MAC("mac"),
        SCORE("score"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen");

        private final String columnName;

        ChangeOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public long countChangeFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IdentityChangeFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_identity_change_findings " +
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

    public List<IdentityChangeFindingEntry> findAllChangeFindings(Filters filters,
                                                                  TimeRange timeRange,
                                                                  ChangeOrderColumn orderColumn,
                                                                  OrderDirection orderDirection,
                                                                  int limit,
                                                                  int offset,
                                                                  UUID organizationId,
                                                                  UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IdentityChangeFindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_change_findings " +
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
                        .mapTo(IdentityChangeFindingEntry.class)
                        .list()
        );
    }

    public Optional<IdentityChangeFindingEntry> findChangeFinding(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_change_findings WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(IdentityChangeFindingEntry.class)
                        .findOne()
        );
    }

    public List<IdentityChangeFindingEntry> findActiveChangeFindingsOfTenant(Handle handle,
                                                                             UUID organizationId,
                                                                             UUID tenantId,
                                                                             DateTime activeSince) {
        return handle.createQuery("SELECT * FROM assets_identity_change_findings " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND last_seen >= :active_since")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("active_since", activeSince)
                .mapTo(IdentityChangeFindingEntry.class)
                .list();
    }

    public long countChangeFindingsOfMac(String mac, UUID organizationId, UUID tenantId, DateTime since) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_identity_change_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac = :mac AND last_seen >= :since")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", since)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IdentityChangeFindingEntry> findChangeFindingsOfMac(String mac, UUID organizationId, UUID tenantId,
                                                                    DateTime since, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_identity_change_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac = :mac AND last_seen >= :since ORDER BY last_seen DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("since", since)
                        .bind("limit", limit)
                        .mapTo(IdentityChangeFindingEntry.class)
                        .list()
        );
    }

    public IdentityChangeFindingEntry createChangeFinding(Handle handle,
                                                          UUID organizationId,
                                                          UUID tenantId,
                                                          String mac,
                                                          int score,
                                                          List<IdentityChange> changes,
                                                          @Nullable String previousFingerprint,
                                                          @Nullable String newFingerprint,
                                                          DateTime firstSeen,
                                                          DateTime lastSeen) {
        return handle.createQuery("INSERT INTO assets_identity_change_findings(uuid, organization_id, " +
                        "tenant_id, mac, score, changes, previous_fingerprint, new_fingerprint, first_seen, " +
                        "last_seen, created_at, updated_at) VALUES(:uuid, :organization_id, :tenant_id, :mac, " +
                        ":score, :changes::jsonb, :previous_fingerprint, :new_fingerprint, :first_seen, " +
                        ":last_seen, NOW(), NOW()) RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("mac", mac)
                .bind("score", score)
                .bind("changes", toJson(changes))
                .bind("previous_fingerprint", previousFingerprint)
                .bind("new_fingerprint", newFingerprint)
                .bind("first_seen", firstSeen)
                .bind("last_seen", lastSeen)
                .mapTo(IdentityChangeFindingEntry.class)
                .one();
    }

    public void updateChangeFinding(Handle handle,
                                    long id,
                                    int score,
                                    List<IdentityChange> changes,
                                    @Nullable String previousFingerprint,
                                    @Nullable String newFingerprint,
                                    DateTime lastSeen) {
        handle.createUpdate("UPDATE assets_identity_change_findings SET score = :score, " +
                        "changes = :changes::jsonb, previous_fingerprint = :previous_fingerprint, " +
                        "new_fingerprint = :new_fingerprint, last_seen = GREATEST(last_seen, :last_seen), " +
                        "updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("score", score)
                .bind("changes", toJson(changes))
                .bind("previous_fingerprint", previousFingerprint)
                .bind("new_fingerprint", newFingerprint)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void deleteChangeFinding(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_change_findings WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteChangeFindings(List<UUID> uuids, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_change_findings WHERE uuid IN (<uuids>) " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteChangeFindings(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new IdentityChangeFindingFilters());

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_identity_change_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

    public enum LinkOrderColumn {
        PREDECESSOR_MAC("predecessor_mac"),
        SUCCESSOR_MAC("successor_mac"),
        KIND("kind"),
        CONFIDENCE("confidence"),
        STATUS("status"),
        SUCCESSOR_FIRST_SEEN("successor_first_seen"),
        DETECTED_AT("detected_at");

        private final String columnName;

        LinkOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    private static final String LINK_TIME_RANGE_CONDITION = "AND detected_at >= :tr_from AND detected_at <= :tr_to ";

    public long countLinks(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetLinkFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_links " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                LINK_TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetLinkEntry> findAllLinks(Filters filters,
                                             TimeRange timeRange,
                                             LinkOrderColumn orderColumn,
                                             OrderDirection orderDirection,
                                             int limit,
                                             int offset,
                                             UUID organizationId,
                                             UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetLinkFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_links " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                LINK_TIME_RANGE_CONDITION + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, detected_at DESC " +
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
                        .mapTo(AssetLinkEntry.class)
                        .list()
        );
    }

    public Optional<AssetLinkEntry> findLink(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_links WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(AssetLinkEntry.class)
                        .findOne()
        );
    }

    public List<AssetLinkEntry> findOpenLinksOfMac(String mac, UUID organizationId, UUID tenantId, int limit) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_links WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND (predecessor_mac = :mac OR successor_mac = :mac) " +
                                "AND status <> :dismissed ORDER BY detected_at DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("mac", mac)
                        .bind("dismissed", LINK_STATUS_DISMISSED)
                        .bind("limit", limit)
                        .mapTo(AssetLinkEntry.class)
                        .list()
        );
    }

    public List<AssetLinkEntry> findAllLinksOfTenant(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM assets_links WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(AssetLinkEntry.class)
                .list();
    }

    // Dismissed links are not refreshed.
    public Optional<AssetLinkEntry> upsertLink(Handle handle,
                                               UUID organizationId,
                                               UUID tenantId,
                                               String predecessorMac,
                                               String successorMac,
                                               String kind,
                                               int confidence,
                                               List<AssetLinkEvidence> evidence,
                                               DateTime predecessorLastSeen,
                                               DateTime successorFirstSeen) {
        return handle.createQuery("INSERT INTO assets_links(uuid, organization_id, tenant_id, predecessor_mac, " +
                        "successor_mac, kind, confidence, evidence, status, predecessor_last_seen, " +
                        "successor_first_seen, detected_at, created_at, updated_at) VALUES(:uuid, " +
                        ":organization_id, :tenant_id, :predecessor_mac, :successor_mac, :kind, :confidence, " +
                        ":evidence::jsonb, :status, :predecessor_last_seen, :successor_first_seen, NOW(), NOW(), " +
                        "NOW()) ON CONFLICT (organization_id, tenant_id, predecessor_mac, successor_mac) " +
                        "DO UPDATE SET confidence = EXCLUDED.confidence, evidence = EXCLUDED.evidence, " +
                        "predecessor_last_seen = EXCLUDED.predecessor_last_seen, updated_at = NOW() " +
                        "WHERE assets_links.status <> :dismissed RETURNING *")
                .bind("uuid", UUID.randomUUID())
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("predecessor_mac", predecessorMac)
                .bind("successor_mac", successorMac)
                .bind("kind", kind)
                .bind("confidence", confidence)
                .bind("evidence", toJson(evidence))
                .bind("status", LINK_STATUS_OPEN)
                .bind("predecessor_last_seen", predecessorLastSeen)
                .bind("successor_first_seen", successorFirstSeen)
                .bind("dismissed", LINK_STATUS_DISMISSED)
                .mapTo(AssetLinkEntry.class)
                .findOne();
    }

    public void markReappearedPredecessorsAsAlternate(Handle handle, UUID organizationId, UUID tenantId,
                                                       int quietMinutes) {
        handle.createUpdate("UPDATE assets_links AS l SET kind = :alternate, updated_at = NOW() FROM assets AS a " +
                        "WHERE a.organization_id = l.organization_id AND a.tenant_id = l.tenant_id " +
                        "AND a.mac = l.predecessor_mac AND l.organization_id = :organization_id " +
                        "AND l.tenant_id = :tenant_id AND l.kind = :successor AND l.status <> :dismissed " +
                        "AND a.last_seen > l.successor_first_seen + make_interval(mins => :quiet_minutes)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("alternate", LINK_KIND_ALTERNATE)
                .bind("successor", LINK_KIND_SUCCESSOR)
                .bind("dismissed", LINK_STATUS_DISMISSED)
                .bind("quiet_minutes", quietMinutes)
                .execute();
    }

    // A check counts from the later of its due time and its creation, so a burst of newly scheduled checks is
    // not overdue at once.
    public SuccessorBacklog successorBacklog() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) AS c, MIN(GREATEST(next_check_at, created_at)) AS oldest " +
                                "FROM assets_identity_successor_checks " +
                                "WHERE next_check_at IS NOT NULL AND next_check_at <= NOW()")
                        .map((rs, ctx) -> new SuccessorBacklog(rs.getLong("c"),
                                rs.getTimestamp("oldest") == null ? null : new DateTime(rs.getTimestamp("oldest"))))
                        .one()
        );
    }

    public record SuccessorBacklog(long dueChecks, @Nullable DateTime oldestDueSince) { }

    public void setLinkKind(Handle handle, long id, String kind) {
        handle.createUpdate("UPDATE assets_links SET kind = :kind, updated_at = NOW() WHERE id = :id")
                .bind("id", id)
                .bind("kind", kind)
                .execute();
    }

    public void setLinkStatus(long id, String status) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_links SET status = :status, updated_at = NOW() WHERE id = :id")
                        .bind("id", id)
                        .bind("status", status)
                        .execute()
        );
    }

    public void setLinksStatus(List<UUID> uuids, String status, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_links SET status = :status, updated_at = NOW() " +
                                "WHERE uuid IN (<uuids>) AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bindList("uuids", uuids)
                        .bind("status", status)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void setLinksStatus(Filters filters, TimeRange timeRange, String status, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetLinkFilters());

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets_links SET status = :status, updated_at = NOW() " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                LINK_TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("status", status)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

    public void deleteLink(long id) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_links WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteLinks(List<UUID> uuids, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_links WHERE uuid IN (<uuids>) " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bindList("uuids", uuids)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteLinks(Filters filters, TimeRange timeRange, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetLinkFilters());

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_links " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                LINK_TIME_RANGE_CONDITION + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

}
