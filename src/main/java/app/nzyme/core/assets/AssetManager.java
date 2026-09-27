package app.nzyme.core.assets;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.assets.db.AssetHostnameEntry;
import app.nzyme.core.assets.db.AssetIpAddressEntry;
import app.nzyme.core.database.OrderDirection;
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
import org.joda.time.DateTime;

import java.net.InetAddress;
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

    public AssetManager(NzymeNode nzyme) {
        this.nzyme = nzyme;
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
                tapUuid,
                DetectionType.ASSETS_NEW,
                subsystem,
                "New asset with MAC address \"" + mac + "\" detected.",
                attributes,
                Set.of("asset_uuid")
        );
    }


    public void attachTransparentContextHostname(String macAddress,
                                                 UUID organizationId,
                                                 UUID tenantId,
                                                 String hostname,
                                                 String source,
                                                 DateTime lastSeen) {
        Optional<AssetEntry> asset = findAssetByMac(macAddress, organizationId, tenantId);

        if (asset.isEmpty()) {
            LOG.debug("MAC address [{}] of transparent context not found in assets. Skipping.", macAddress);
            return;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO assets_hostnames(asset_id, uuid, hostname, source, first_seen, " +
                                "last_seen) VALUES(:asset_id, :uuid, :hostname, :source, :first_seen, :last_seen) " +
                                "ON CONFLICT (asset_id, hostname, source) DO UPDATE " +
                                "SET last_seen = GREATEST(assets_hostnames.last_seen, EXCLUDED.last_seen)")
                        .bind("asset_id", asset.get().id())
                        .bind("uuid", UUID.randomUUID())
                        .bind("hostname", hostname)
                        .bind("source", source)
                        .bind("first_seen", lastSeen) // Same for INSERT, ignored in UPDATE.
                        .bind("last_seen", lastSeen)
                        .execute()
        );
    }

    public void attachTransparentContextIpAddress(String macAddress,
                                                  UUID organizationId,
                                                  UUID tenantId,
                                                  InetAddress address,
                                                  String source,
                                                  DateTime lastSeen) {
        Optional<AssetEntry> asset = findAssetByMac(macAddress, organizationId, tenantId);

        if (asset.isEmpty()) {
            LOG.debug("MAC address [{}] of transparent context not found in assets. Skipping.", macAddress);
            return;
        }

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO assets_ip_addresses(asset_id, uuid, address, source, first_seen, " +
                                "last_seen) VALUES(:asset_id, :uuid, :address, :source, :first_seen, :last_seen) " +
                                "ON CONFLICT (asset_id, address, source) DO UPDATE " +
                                "SET last_seen = GREATEST(assets_ip_addresses.last_seen, EXCLUDED.last_seen)")
                        .bind("asset_id", asset.get().id())
                        .bind("uuid", UUID.randomUUID())
                        .bind("address", address)
                        .bind("source", source)
                        .bind("first_seen", lastSeen) // Same for INSERT, ignored in UPDATE.
                        .bind("last_seen", lastSeen)
                        .execute()
        );
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

    public void retentionCleanAssetStatistics(UUID organizationId, UUID tenantId, DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM assets_statistics WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND timestamp < :cutoff")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", cutoff)
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
