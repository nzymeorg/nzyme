package app.nzyme.core.assets;

import app.nzyme.core.assets.identity.AssetIdentity;
import app.nzyme.core.assets.profile.AssetProfiles;
import app.nzyme.core.assets.tags.AssetTags;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.db.AssetHostnameEntry;
import app.nzyme.core.assets.db.AssetIpAddressEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.net.InetAddress;
import javax.annotation.Nullable;
import java.util.*;

public class AssetManager {

    private static final Logger LOG = LogManager.getLogger(AssetManager.class);

    private static final int ACTIVE_ASSET_TIMEOUT_MINUTES = 30;

    public enum OrderColumn {

        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen"),
        MAC("mac");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum HostnameOrderColumn {

        HOSTNAME("hostname"),
        SOURCE("source"),
        LAST_SEEN("last_seen"),
        FIRST_SEEN("first_seen");

        private final String columnName;

        HostnameOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    public enum IpAddressOrderColumn {

        ADDRESS("address"),
        SOURCE("source"),
        LAST_SEEN("last_seen"),
        FIRST_SEEN("first_seen");

        private final String columnName;

        IpAddressOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    private final NzymeNode nzyme;

    private final AssetProfiles profiles;
    private final AssetIdentity identity;
    private final AssetTags tags;

    public AssetManager(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.profiles = new AssetProfiles(nzyme);
        this.identity = new AssetIdentity(nzyme);
        this.tags = new AssetTags(nzyme);
    }

    public AssetProfiles profiles() {
        return profiles;
    }

    public AssetIdentity identity() {
        return identity;
    }

    public AssetTags tags() {
        return tags;
    }

    public long countActiveAssets(UUID organizationId, UUID tenantId) {
        DateTime now = DateTime.now();

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND last_seen >= :tr_from AND last_seen <= :tr_to")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", now.minusMinutes(ACTIVE_ASSET_TIMEOUT_MINUTES))
                        .bind("tr_to", now)
                        .mapTo(Long.class)
                        .one()
        );
    }
    
    public long countAssets(TimeRange timeRange, Filters filters, UUID organizationId, UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT a.id " + ASSET_JOINS + LIST_WHERE +
                                filterFragment.whereSql() + " " +
                                "GROUP BY a.id HAVING TRUE " + filterFragment.havingSql() + ") AS filtered")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    // This reads from the `asset_statistics` table, which is on an independent retention schedule.
    public List<GenericIntegerHistogramEntry> activeAssetCountHistogram(TimeRange timeRange,
                                                                        Bucketing.BucketingConfiguration bucketing,
                                                                        UUID organizationId,
                                                                        UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, " +
                                "MAX(asset_count) AS value FROM assets_statistics " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "GROUP BY bucket ORDER BY bucket DESC;")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public List<AssetEntry> findAllAssets(UUID organizationId,
                                          UUID tenantId,
                                          TimeRange timeRange,
                                          Filters filters,
                                          int limit,
                                          int offset,
                                          OrderColumn orderColumn,
                                          OrderDirection orderDirection) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + ASSET_COLUMNS + ASSET_JOINS + LIST_WHERE +
                                filterFragment.whereSql() + " " +
                                "GROUP BY a.id HAVING TRUE " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindMap(filterFragment.bindings())
                        .mapTo(AssetEntry.class)
                        .list()
        );
    }

    public long countAllInactiveAssets(TimeRange timeRange,
                                       Filters filters,
                                       UUID organizationId,
                                       UUID tenantId) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM (SELECT a.id " + ASSET_JOINS + LIST_WHERE +
                                filterFragment.whereSql() + " AND " + INACTIVE_CONDITION + " " +
                                "GROUP BY a.id HAVING TRUE " + filterFragment.havingSql() + ") AS filtered")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetEntry> findAllInactiveAssets(UUID organizationId,
                                                  UUID tenantId,
                                                  TimeRange timeRange,
                                                  Filters filters,
                                                  int limit,
                                                  int offset,
                                                  OrderColumn orderColumn,
                                                  OrderDirection orderDirection) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new AssetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + ASSET_COLUMNS + ASSET_JOINS + LIST_WHERE +
                                filterFragment.whereSql() + " AND " + INACTIVE_CONDITION + " " +
                                "GROUP BY a.id HAVING TRUE " + filterFragment.havingSql() + " " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bindMap(filterFragment.bindings())
                        .mapTo(AssetEntry.class)
                        .list()
        );
    }

    @Nullable
    private static String dhcpFingerprintColumn(String transactionType) {
        return switch (transactionType) {
            case "Initial" -> "dhcp_fingerprint_initial";
            case "Renew" -> "dhcp_fingerprint_renew";
            case "Reboot" -> "dhcp_fingerprint_reboot";
            case "Rebind" -> "dhcp_fingerprint_rebind";
            default -> null;
        };
    }

    @Nullable
    public static String dhcpFingerprintOf(AssetEntry asset, String transactionType) {
        return switch (transactionType) {
            case "Initial" -> asset.dhcpFingerprintInitial();
            case "Renew" -> asset.dhcpFingerprintRenew();
            case "Reboot" -> asset.dhcpFingerprintReboot();
            case "Rebind" -> asset.dhcpFingerprintRebind();
            default -> null;
        };
    }

    public void setDhcpFingerprint(long assetId, String transactionType, String fingerprint) {
        String column = dhcpFingerprintColumn(transactionType);
        if (column == null) {
            throw new IllegalArgumentException("Unknown DHCP transaction type.");
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE assets SET " + column + " = :fingerprint, updated_at = NOW() " +
                                "WHERE id = :id")
                        .bind("fingerprint", fingerprint)
                        .bind("id", assetId)
                        .execute()
        );
    }

    public Optional<AssetEntry> findAsset(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + ASSET_COLUMNS + ASSET_JOINS +
                                "WHERE a.uuid = :uuid AND a.organization_id = :organization_id " +
                                "AND a.tenant_id = :tenant_id " +
                                "GROUP BY a.id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(AssetEntry.class)
                        .findOne()
        );
    }

    public Optional<AssetEntry> findAssetByMac(String mac, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + ASSET_COLUMNS + ASSET_JOINS +
                                "WHERE a.mac = :mac AND a.organization_id = :organization_id " +
                                "AND a.tenant_id = :tenant_id " +
                                "GROUP BY a.id")
                        .bind("mac", mac)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(AssetEntry.class)
                        .findOne()
        );
    }

    // The TCP and UDP sources record the source MAC of a packet. A router is the source MAC of everything it
    // forwards and is recorded with every address behind it, so an asset only counts as the device that has an
    // address if it claimed the address by ARP.
    public static final String IP_ADDRESS_CLAIM_SOURCE = "Arp";

    // For a row of assets_ip_addresses: if the asset of the row claimed the address of the row.
    public static String ipAddressClaimed(String alias) {
        return "EXISTS (SELECT 1 FROM assets_ip_addresses AS claim WHERE claim.asset_id = " + alias + ".asset_id " +
                "AND claim.address = " + alias + ".address AND claim.source = '" + IP_ADDRESS_CLAIM_SOURCE + "') ";
    }

    public boolean claimedIpAddress(String mac, InetAddress ip, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS (SELECT 1 FROM assets_ip_addresses AS i " +
                                "JOIN assets AS a ON a.id = i.asset_id WHERE a.mac = :mac " +
                                "AND a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                                "AND i.address = :ip AND i.source = :source)")
                        .bind("mac", mac)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("ip", ip)
                        .bind("source", IP_ADDRESS_CLAIM_SOURCE)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    public long countAssetsOfIpAddress(InetAddress ip, UUID organizationId, DateTime ipActivityCutoff, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(DISTINCT a.id) FROM assets AS a " +
                                "INNER JOIN assets_ip_addresses AS i ON i.asset_id = a.id " +
                                "WHERE i.address = :ip AND i.last_seen > :ip_activity_cutoff " +
                                "AND " + ipAddressClaimed("i") +
                                "AND a.organization_id = :organization_id AND a.tenant_id = :tenant_id")
                        .bind("ip", ip)
                        .bind("ip_activity_cutoff", ipActivityCutoff)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetEntry> findAssetsByIpAddress(InetAddress ip,
                                                  UUID organizationId,
                                                  UUID tenantId,
                                                  DateTime ipActivityCutoff,
                                                  int limit,
                                                  int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT " + ASSET_COLUMNS + ASSET_JOINS +
                                "WHERE a.id IN (SELECT used.asset_id FROM assets_ip_addresses AS used " +
                                "WHERE used.address = :ip AND used.last_seen > :ip_activity_cutoff " +
                                "AND " + ipAddressClaimed("used") + ") " +
                                "AND a.organization_id = :organization_id " +
                                "AND a.tenant_id = :tenant_id " +
                                "GROUP BY a.id ORDER BY a.last_seen DESC LIMIT :limit OFFSET :offset")
                        .bind("ip", ip)
                        .bind("ip_activity_cutoff", ipActivityCutoff)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(AssetEntry.class)
                        .list()
        );
    }

    /*
     * The taps that recorded activity of a MAC address since the given time, and the taps that last did. The latest
     * bucket is taken from the rows of the MAC address: MAX(bucket) in a subquery is answered from the index on
     * the bucket alone, which reads every bucket of all MAC addresses when this one has no recent activity.
     */
    public List<UUID> findTapsOfMac(Handle handle, String mac, UUID organizationId, UUID tenantId, DateTime since) {
        return handle.createQuery("SELECT DISTINCT tap_uuid FROM (SELECT tap_uuid, bucket, " +
                        "MAX(bucket) OVER () AS latest FROM assets_activity " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND mac = :mac) AS a " +
                        "WHERE bucket = latest OR bucket >= date_trunc('hour', :since::timestamptz)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("mac", mac)
                .bind("since", since)
                .mapTo(UUID.class)
                .list();
    }

    public void onNewAsset(Subsystem subsystem,
                           UUID assetUuid,
                           String mac,
                           UUID organizationId,
                           UUID tenantId,
                           UUID tapUuid) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("asset_uuid", assetUuid.toString());
        attributes.put("mac", mac);

        // TODO only if alert enabled.
        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                AlertOrigin.tap(tapUuid),
                DetectionType.ASSETS_NEW,
                subsystem,
                "New asset with MAC address \"" + mac + "\" detected.",
                attributes,
                Set.of("asset_uuid")
        );
    }

    /*
     * Cheap lookup for callers that only need the asset ID and not the aggregated hostnames/IP addresses
     * that findAssetByMac() builds.
     */
    public Optional<Long> findAssetIdByMac(Handle handle, String mac, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT id FROM assets WHERE mac = :mac AND organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("mac", mac)
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(Long.class)
                .findOne();
    }

    public void attachTransparentContextHostname(Handle handle,
                                                 String macAddress,
                                                 UUID organizationId,
                                                 UUID tenantId,
                                                 String hostname,
                                                 String source,
                                                 DateTime lastSeen) {
        Optional<Long> assetId = findAssetIdByMac(handle, macAddress, organizationId, tenantId);

        if (assetId.isEmpty()) {
            LOG.debug("MAC address [{}] of transparent context not found in assets. Skipping.", macAddress);
            return;
        }

        handle.createUpdate("INSERT INTO assets_hostnames(asset_id, uuid, hostname, source, first_seen, " +
                        "last_seen) VALUES(:asset_id, :uuid, :hostname, :source, :first_seen, :last_seen) " +
                        "ON CONFLICT (asset_id, hostname, source) DO UPDATE " +
                        "SET last_seen = GREATEST(assets_hostnames.last_seen, EXCLUDED.last_seen)")
                .bind("asset_id", assetId.get())
                .bind("uuid", UUID.randomUUID())
                .bind("hostname", hostname)
                .bind("source", source)
                .bind("first_seen", lastSeen)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void attachTransparentContextIpAddress(Handle handle,
                                                  String macAddress,
                                                  UUID organizationId,
                                                  UUID tenantId,
                                                  InetAddress address,
                                                  String source,
                                                  DateTime lastSeen) {
        Optional<Long> assetId = findAssetIdByMac(handle, macAddress, organizationId, tenantId);

        if (assetId.isEmpty()) {
            LOG.debug("MAC address [{}] of transparent context not found in assets. Skipping.", macAddress);
            return;
        }

        handle.createUpdate("INSERT INTO assets_ip_addresses(asset_id, uuid, address, source, first_seen, " +
                        "last_seen) VALUES(:asset_id, :uuid, :address, :source, :first_seen, :last_seen) " +
                        "ON CONFLICT (asset_id, address, source) DO UPDATE " +
                        "SET last_seen = GREATEST(assets_ip_addresses.last_seen, EXCLUDED.last_seen)")
                .bind("asset_id", assetId.get())
                .bind("uuid", UUID.randomUUID())
                .bind("address", address)
                .bind("source", source)
                .bind("first_seen", lastSeen)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public long countHostnamesOfAsset(long assetId, TimeRange timeRange) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_hostnames " +
                                "WHERE asset_id = :asset_id AND last_seen >= :tr_from AND last_seen <= :tr_to")
                        .bind("asset_id", assetId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetHostnameEntry> findHostnamesOfAsset(long assetId,
                                                         TimeRange timeRange,
                                                         int limit,
                                                         int offset,
                                                         HostnameOrderColumn orderColumn,
                                                         OrderDirection orderDirection) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_hostnames WHERE asset_id = :asset_id " +
                                "AND last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("asset_id", assetId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(AssetHostnameEntry.class)
                        .list()
        );
    }

    public void deleteHostnameOfAsset(long assetId, UUID hostnameId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_hostnames WHERE asset_id = :asset_id AND uuid = :uuid")
                        .bind("asset_id", assetId)
                        .bind("uuid", hostnameId)
                        .execute()
        );
    }

    public long countIpAddressesOfAsset(long assetId, TimeRange timeRange) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM assets_ip_addresses " +
                                "WHERE asset_id = :asset_id AND last_seen >= :tr_from AND last_seen <= :tr_to")
                        .bind("asset_id", assetId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<AssetIpAddressEntry> findIpAddressesOfAsset(long assetId,
                                                            TimeRange timeRange,
                                                            int limit,
                                                            int offset,
                                                            IpAddressOrderColumn orderColumn,
                                                            OrderDirection orderDirection) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM assets_ip_addresses WHERE asset_id = :asset_id " +
                                "AND last_seen >= :tr_from AND last_seen <= :tr_to " +
                                "ORDER BY <order_column> <order_direction> " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("asset_id", assetId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .mapTo(AssetIpAddressEntry.class)
                        .list()
        );
    }

    public void deleteIpAddressOfAsset(long assetId, UUID addressId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_ip_addresses WHERE asset_id = :asset_id AND uuid = :uuid")
                        .bind("asset_id", assetId)
                        .bind("uuid", addressId)
                        .execute()
        );
    }

    public void writeAssetStatistics(UUID organizationId, UUID tenantId, long assetCount, DateTime timestamp) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO assets_statistics(organization_id, tenant_id, asset_count, " +
                                "timestamp) VALUES(:organization_id, :tenant_id, :asset_count, :timestamp)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("asset_count", assetCount)
                        .bind("timestamp", timestamp)
                        .execute()
        );
    }

    public static final String ACTIVE_CONDITION =
            "(a.last_seen >= (NOW() - interval '" + ACTIVE_ASSET_TIMEOUT_MINUTES + " minute'))";

    private static final String INACTIVE_CONDITION =
            "(a.last_seen < (NOW() - interval '" + ACTIVE_ASSET_TIMEOUT_MINUTES + " minute'))";

    private static final String ASSET_COLUMNS =
            "a.*, " + ACTIVE_CONDITION + " AS is_active, " +
                    "COALESCE(array_agg(DISTINCT h.hostname) FILTER (WHERE h.hostname IS NOT NULL), '{}') AS hostnames, " +
                    "COALESCE(array_agg(DISTINCT host(i.address)) FILTER (WHERE i.address IS NOT NULL), '{}') AS ip_addresses ";

    private static final String ASSET_JOINS =
            "FROM assets AS a " +
                    "LEFT JOIN assets_hostnames AS h ON h.asset_id = a.id " +
                    "LEFT JOIN assets_ip_addresses AS i ON i.asset_id = a.id ";

    private static final String LIST_WHERE =
            "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                    "AND a.last_seen >= :tr_from AND a.last_seen <= :tr_to ";


}
