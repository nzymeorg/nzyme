package app.nzyme.core.detection.alerts;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringNumberAggregationResult;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertSearchEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertTimelineEntry;
import app.nzyme.core.events.types.DetectionEvent;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class DetectionAlertService {

    private static final Logger LOG = LogManager.getLogger(DetectionAlertService.class);

    public static final int ACTIVE_THRESHOLD_MINUTES = 5;

    /*
     * Attribute keys that carry the address or MAC of the device an alert is about, in order of preference. The
     * first key an alert has a value for is its affected address or MAC. The same lists drive the search query
     * and the responses, so the filters match what is displayed.
     */
    public static final List<String> AFFECTED_ADDRESS_KEYS = List.of(
            "source_address", "client_address", "sender_address", "address", "target_address", "server_address",
            "destination_address", "gateway_address"
    );
    public static final List<String> AFFECTED_MAC_KEYS = List.of(
            "source_mac", "client_mac", "sender_mac", "spoofer_mac", "mac", "owner_mac", "responder_mac",
            "server_mac", "bssid"
    );

    public enum OrderColumn {

        STATUS("status"),
        DETAILS("details"),
        DETECTION_TYPE("detection_type"),
        SUBSYSTEM("subsystem"),
        AFFECTED_ADDRESS("affected_address"),
        AFFECTED_MAC("affected_mac"),
        REACTIVATIONS("reactivations"),
        CREATED_AT("created_at"),
        LAST_SEEN("last_seen");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }

    }

    /*
     * Alerts of a tenant whose lifetime overlaps the time range, with the values the search filters, sorts and
     * aggregates on. An address attribute is only cast to inet when it looks like one, so a stray value cannot
     * break the whole query.
     */
    private static final String SEARCH_BASE =
            "SELECT da.*, " +
                    "CASE WHEN da.is_resolved THEN 'Resolved' " +
                    "WHEN da.last_seen > NOW() - INTERVAL '" + ACTIVE_THRESHOLD_MINUTES + " minutes' THEN 'Active' " +
                    "ELSE 'Inactive' END AS status, " +
                    "(" + affectedAttributeSelect(AFFECTED_ADDRESS_KEYS,
                            "AND (aa.attribute_value ~ '^(\\d{1,3}\\.){3}\\d{1,3}$' " +
                                    "OR aa.attribute_value ~ '^[0-9a-fA-F:]+$') ") +
                    ")::inet AS affected_address, " +
                    "(" + affectedAttributeSelect(AFFECTED_MAC_KEYS, "") + ") AS affected_mac, " +
                    "(SELECT COUNT(*) FROM detection_alert_timeline AS t WHERE t.detection_alert_id = da.id) - 1 " +
                    "AS reactivations " +
                    "FROM detection_alerts AS da " +
                    "WHERE da.organization_id = :organization_id AND da.tenant_id = :tenant_id " +
                    "AND da.last_seen >= :tr_from AND da.created_at <= :tr_to ";

    private static final String BUCKETS =
            "buckets AS (SELECT generate_series(" +
                    "date_trunc(:date_trunc, :tr_from::timestamptz), " +
                    "date_trunc(:date_trunc, :tr_to::timestamptz), " +
                    "make_interval(secs => :bucket_size_s)) AS bucket) ";

    private static String affectedAttributeSelect(List<String> keys, String additionalCondition) {
        String array = "ARRAY['" + String.join("','", keys) + "']::text[]";

        return "SELECT aa.attribute_value FROM detection_alert_attributes AS aa " +
                "WHERE aa.detection_alert_id = da.id AND aa.attribute_key = ANY(" + array + ") " +
                "AND aa.attribute_value <> '' " + additionalCondition +
                "ORDER BY array_position(" + array + ", aa.attribute_key) LIMIT 1";
    }

    private final NzymeNode nzyme;

    public DetectionAlertService(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public int countAllDetectionTypes(@Nullable Subsystem subsystem) {
        int count = 0;
        for (DetectionType t : DetectionType.values()) {
            if (subsystem == null || t.getSubsystem().equals(subsystem)) {
                count++;
            }
        }

        return count;
    }

    public List<DetectionType> findAllDetectionTypes(@Nullable Subsystem subsystem,
                                                     int limit,
                                                     int offset) {
        List<DetectionType> result = Lists.newArrayList();
        for (DetectionType t : DetectionType.values()) {
            if (subsystem == null || t.getSubsystem().equals(subsystem)) {
                result.add(t);
            }
        }

        return result.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void raiseAlert(UUID organizationId,
                           UUID tenantId,
                           @Nullable UUID dot11MonitoredNetworkId,
                           @Nullable UUID tapId,
                           DetectionType detectionType,
                           Subsystem subsystem,
                           String details,
                           Map<String, String> attributes,
                           Set<String> comparisonAttributeKeys) {
        if (organizationId == null || tenantId == null) {
            throw new RuntimeException("Detection alerts must have organization and tenant UUID set.");
        }

        TreeMap<String, String> comparisonAttributes = Maps.newTreeMap();
        for (String key : comparisonAttributeKeys) {
            String value = attributes.get(key);
            if (value == null) {
                throw new RuntimeException("Comparison attribute key [" + key + "] not in attributes." );
            }
            comparisonAttributes.put(key, value);
        }

        String comparisonChecksum = buildChecksum(
                organizationId,
                tenantId,
                dot11MonitoredNetworkId,
                detectionType,
                subsystem,
                comparisonAttributes
        );

        Optional<DetectionAlertEntry> existingAlert = findAlertWithComparisonChecksum(comparisonChecksum);
        if (existingAlert.isPresent()) {
            // This alert has been raised in the past.
            LOG.debug("Alert of type [{}] with checksum [{}] is not new. Updating.",
                    detectionType.name(), comparisonChecksum);

            nzyme.getDatabase().withHandle(handle ->
                    handle.createUpdate("UPDATE detection_alerts SET last_seen = NOW(), is_resolved = false " +
                                    "WHERE id = :id")
                            .bind("id", existingAlert.get().id())
                            .execute()
            );

            // Update alert attributes.
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                nzyme.getDatabase().useHandle(handle ->
                        handle.createUpdate("UPDATE detection_alert_attributes " +
                                        "SET attribute_value = :attribute_value " +
                                        "WHERE detection_alert_id = :detection_alert_id " +
                                        "AND attribute_key = :attribute_key")
                                .bind("detection_alert_id", existingAlert.get().id())
                                .bind("attribute_key", attribute.getKey())
                                .bind("attribute_value", attribute.getValue())
                                .execute()
                );
            }

            /*
             * Write alert timeline.
             *
             * To show when a potentially re-activated alert was seen, we store a timeline of alerts. A new timeline
             * entry starts when the alert is not currently active. If the alert is currently active, the current
             * timeline entry is extended. This allows for very easy querying.
             */
            if (existingAlert.get().lastSeen()
                    .isAfter(DateTime.now().minusMinutes(DetectionAlertService.ACTIVE_THRESHOLD_MINUTES))) {
                // Active alert. Extend existing timeline entry.
                nzyme.getDatabase().useHandle(handle ->
                        handle.createUpdate("UPDATE detection_alert_timeline SET seen_to = NOW() " +
                                        "WHERE id = (SELECT MAX(id) FROM detection_alert_timeline " +
                                        "WHERE detection_alert_id = :detection_alert_id)")
                                .bind("detection_alert_id", existingAlert.get().id())
                                .execute()
                );
            } else {
                // Inactive alert. Create new timeline entry.
                createAlertTimelineEntry(existingAlert.get().id());

                // Create event.
                nzyme.getEventEngine().processEvent(
                        DetectionEvent.create(existingAlert.get().uuid(), detectionType, details, DateTime.now()),
                        organizationId,
                        tenantId
                );
            }

            return;
        }

        // New alert / not re-triggered.
        UUID alertUUID = UUID.randomUUID();
        long alertId = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("INSERT INTO detection_alerts(uuid, organization_id, tenant_id, " +
                                "dot11_monitored_network_id, tap_id, detection_type, subsystem, comparison_checksum, " +
                                "details, created_at, last_seen) VALUES(:uuid, :organization_id, :tenant_id, " +
                                ":dot11_monitored_network_id, :tap_id, :detection_type, :subsystem, " +
                                ":comparison_checksum, :details, NOW(), NOW()) RETURNING id")
                        .bind("uuid", alertUUID)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("dot11_monitored_network_id", dot11MonitoredNetworkId)
                        .bind("tap_id", tapId)
                        .bind("detection_type", detectionType.name())
                        .bind("subsystem", subsystem)
                        .bind("comparison_checksum", comparisonChecksum)
                        .bind("details", details)
                        .mapTo(Long.class)
                        .one()
        );

        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            nzyme.getDatabase().useHandle(handle ->
                    handle.createUpdate("INSERT INTO detection_alert_attributes(detection_alert_id, " +
                                    "attribute_key, attribute_value) VALUES(:detection_alert_id, :attribute_key, " +
                                    ":attribute_value)")
                            .bind("detection_alert_id", alertId)
                            .bind("attribute_key", attribute.getKey())
                            .bind("attribute_value", attribute.getValue())
                            .execute()
            );
        }

        // Write initial alert timeline entry. See comment in re-raised alert update above.
        createAlertTimelineEntry(alertId);

        // Create event.
        nzyme.getEventEngine().processEvent(
                DetectionEvent.create(alertUUID, detectionType, details, DateTime.now()),
                organizationId,
                tenantId
        );
    }

    private String buildSubsystemSelectorFragment(@Nullable Subsystem subsystem) {
        if (subsystem != null) {
            switch (subsystem) {
                case DOT11 -> { return "subsystem = 'DOT11'"; }
                case ETHERNET -> { return "subsystem = 'ETHERNET'"; }
                case BLUETOOTH -> { return "subsystem = 'BLUETOOTH'"; }
                case UAV -> { return "subsystem = 'UAV'"; }
                default -> { return "1=1"; }
            }
        } else {
            return "1=1";
        }
    }

    public List<DetectionAlertEntry> findAllAlerts(@Nullable UUID organizationId,
                                                   @Nullable UUID tenantId,
                                                   @Nullable Subsystem subsystem,
                                                   int limit,
                                                   int offset) {
        String subsystemSelector = buildSubsystemSelectorFragment(subsystem);

        return nzyme.getDatabase().withHandle(handle -> {
            Query query;
            if (organizationId == null && tenantId == null) {
                // Super Admin.
                query = handle.createQuery("SELECT * FROM detection_alerts WHERE " + subsystemSelector + " " +
                                "ORDER BY last_seen DESC LIMIT :limit OFFSET :offset")
                        .bind("limit", limit)
                        .bind("offset", offset);
            } else if (organizationId != null && tenantId == null) {
                // Organization Admin.
                query = handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND " + subsystemSelector + " " +
                                "ORDER BY last_seen DESC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("limit", limit)
                        .bind("offset", offset);
            } else {
                // Tenant User.
                query = handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND " + subsystemSelector + " " +
                                "ORDER BY last_seen DESC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("limit", limit)
                        .bind("offset", offset);
            }

            return query.mapTo(DetectionAlertEntry.class).list();
        });
    }

    public List<DetectionAlertEntry> findAllActiveAlertsOfMonitoredNetwork(UUID monitoredNetworkId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE dot11_monitored_network_id = :network_id " +
                                "AND last_seen > :cutoff AND is_resolved = false")
                        .bind("network_id", monitoredNetworkId)
                        .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES))
                        .mapTo(DetectionAlertEntry.class)
                        .list()
        );
    }

    public long countAlerts(UUID organizationId, UUID tenantId, @Nullable Subsystem subsystem) {
        String subsystemSelector = buildSubsystemSelectorFragment(subsystem);

        return nzyme.getDatabase().withHandle(handle -> {
            Query query;
            if (organizationId == null && tenantId == null) {
                // Super Admin.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts WHERE " + subsystemSelector);
            } else if (organizationId != null && tenantId == null) {
                // Organization Admin.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND " + subsystemSelector)
                        .bind("organization_id", organizationId);
            } else {
                // Tenant User.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND " + subsystemSelector)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId);
            }

            return query.mapTo(Long.class).one();
        });
    }

    public long countActiveAlerts(UUID organizationId, UUID tenantId, @Nullable Subsystem subsystem) {
        String subsystemSelector = buildSubsystemSelectorFragment(subsystem);

        return nzyme.getDatabase().withHandle(handle -> {
            Query query;
            if (organizationId == null && tenantId == null) {
                // Super Admin.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE is_resolved = false AND last_seen > :cutoff AND " + subsystemSelector)
                        .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES));
            } else if (organizationId != null && tenantId == null) {
                // Organization Admin.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE organization_id = :organization_id " +
                                "AND is_resolved = false AND last_seen > :cutoff AND " + subsystemSelector)
                        .bind("organization_id", organizationId)
                        .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES));
            } else {
                // Tenant User.
                query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND is_resolved = false AND last_seen > :cutoff AND " + subsystemSelector)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES));
            }

            return query.mapTo(Long.class).one();
        });
    }

    public long countActiveAlertsOfTap(UUID organizationId, UUID tenantId, UUID tapId) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("SELECT COUNT(*) FROM detection_alerts WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND is_resolved = false AND last_seen > :cutoff " +
                            "AND tap_id = :tap_id")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("tap_id", tapId)
                    .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES))
                    .mapTo(Long.class)
                    .one()
        );
    }

    public List<DetectionAlertEntry> findActiveAlertsOfTaps(UUID organizationId, UUID tenantId, List<UUID> taps, int limit) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT* FROM detection_alerts WHERE organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id AND is_resolved = false AND last_seen > :cutoff " +
                                "AND tap_id IN (<taps>) ORDER BY last_seen DESC LIMIT :limit")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bindList("taps", taps)
                        .bind("cutoff", DateTime.now().minusMinutes(ACTIVE_THRESHOLD_MINUTES))
                        .bind("limit", limit)
                        .mapTo(DetectionAlertEntry.class)
                        .list()
        );
    }

    // TODO the org/tenant handling here can probably go away as we are now enforcing org and tenant selection in UI
    public Optional<DetectionAlertEntry> findAlert(UUID uuid,
                                                   @Nullable UUID organizationId,
                                                   @Nullable UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> {
            Query query;
            if (organizationId == null && tenantId == null) {
                // Super Admin.
                query = handle.createQuery("SELECT * FROM detection_alerts WHERE uuid = :uuid")
                        .bind("uuid", uuid);
            } else if (organizationId != null && tenantId == null) {
                // Organization Admin.
                query = handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("uuid", uuid);
            } else {
                // Tenant User.
                query = handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", uuid);
            }

            return query.mapTo(DetectionAlertEntry.class).findOne();
        });
    }

    public List<DetectionAlertAttributeEntry> findAlertAttributes(long alertId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM detection_alert_attributes " +
                                "WHERE detection_alert_id = :detection_alert_id")
                        .bind("detection_alert_id", alertId)
                        .mapTo(DetectionAlertAttributeEntry.class)
                        .list()
        );
    }

    public List<DetectionAlertEntry> findAllAlertsByTypeAndAttribute(UUID organizationId,
                                                                     UUID tenantId,
                                                                     String detectionType,
                                                                     String attributeKey,
                                                                     String attributeValue,
                                                                     int limit,
                                                                     int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT da.* FROM detection_alerts AS da " +
                                "LEFT JOIN detection_alert_attributes aa on da.id = aa.detection_alert_id " +
                                "WHERE da.detection_type = :detection_type " +
                                "AND da.organization_id = :organization_id AND da.tenant_id = :tenant_id " +
                                "AND aa.attribute_key = :attribute_key AND aa.attribute_value = :attribute_value " +
                                "ORDER BY da.last_seen DESC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("detection_type", detectionType)
                        .bind("attribute_key", attributeKey)
                        .bind("attribute_value", attributeValue)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DetectionAlertEntry.class)
                        .list()
        );
    }

    public void delete(UUID uuid) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM detection_alerts WHERE uuid = :uuid")
                        .bind("uuid", uuid)
                        .execute()
        );
    }

    public void markAlertAsResolved(UUID uuid) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE detection_alerts SET is_resolved = true WHERE uuid = :uuid")
                        .bind("uuid", uuid)
                        .execute()
        );
    }

    public List<DetectionAlertTimelineEntry> findAlertTimeline(long alertId,
                                                               int limit,
                                                               int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM detection_alert_timeline " +
                                "WHERE detection_alert_id = :detection_alert_id " +
                                "ORDER BY seen_to DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("detection_alert_id", alertId)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DetectionAlertTimelineEntry.class)
                        .list()
        );
    }

    public long countAlertTimelineEntries(long alertId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM detection_alert_timeline " +
                                "WHERE detection_alert_id = :detection_alert_id")
                        .bind("detection_alert_id", alertId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    private Optional<DetectionAlertEntry> findAlertWithComparisonChecksum(String comparisonChecksum) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM detection_alerts " +
                                "WHERE comparison_checksum = :comparison_checksum")
                        .bind("comparison_checksum", comparisonChecksum)
                        .mapTo(DetectionAlertEntry.class)
                        .findOne()
        );
    }

    private void createAlertTimelineEntry(long alertId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO detection_alert_timeline(detection_alert_id, seen_from, " +
                                "seen_to) VALUES(:detection_alert_id, NOW(), NOW())")
                        .bind("detection_alert_id", alertId)
                        .execute()
        );
    }

    private String buildChecksum(@Nullable UUID organizationId,
                                 @Nullable UUID tenantId,
                                 @Nullable UUID dot11MonitoredNetworkId,
                                 DetectionType detectionType,
                                 Subsystem subsystem,
                                 TreeMap<String, String> checksumAttributes) {
        StringBuilder source = new StringBuilder();

        if (organizationId != null) {
            source.append(organizationId);
        }

        if (tenantId != null) {
            source.append(tenantId);
        }

        if (dot11MonitoredNetworkId != null) {
            source.append(dot11MonitoredNetworkId);
        }

        source.append(detectionType.name())
                .append(subsystem);

        for (Map.Entry<String, String> attr : checksumAttributes.entrySet()) {
            source.append(attr.getKey())
                    .append(attr.getValue());
        }

        return Hashing.sha256()
                .hashString(source.toString(), StandardCharsets.UTF_8)
                .toString();
    }


    /*
     * Alert search. The "alerts" CTE carries every alert of the tenant in the time range that matches the filters.
     */
    private String searchCte(FilterSqlFragment filterFragment) {
        return "WITH base AS (" + SEARCH_BASE + "), " +
                "alerts AS (SELECT * FROM base AS alerts WHERE 1=1 " + filterFragment.whereSql() + ") ";
    }

    private Query bindSearch(Query query,
                             UUID organizationId,
                             UUID tenantId,
                             TimeRange timeRange,
                             FilterSqlFragment filterFragment) {
        return query
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindMap(filterFragment.bindings());
    }

    public long countAlerts(UUID organizationId, UUID tenantId, TimeRange timeRange, Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + "SELECT COUNT(*) FROM alerts"),
                        organizationId, tenantId, timeRange, f)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public long countActiveAlerts(UUID organizationId, UUID tenantId, TimeRange timeRange, Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + "SELECT COUNT(*) FROM alerts WHERE status = 'Active'"),
                        organizationId, tenantId, timeRange, f)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DetectionAlertSearchEntry> findAlerts(UUID organizationId,
                                                      UUID tenantId,
                                                      TimeRange timeRange,
                                                      Filters filters,
                                                      OrderColumn orderColumn,
                                                      OrderDirection orderDirection,
                                                      int limit,
                                                      int offset) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + "SELECT * FROM alerts " +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, last_seen DESC " +
                                "LIMIT :limit OFFSET :offset"),
                        organizationId, tenantId, timeRange, f)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DetectionAlertSearchEntry.class)
                        .list()
        );
    }

    /*
     * How many matching alerts were active in each bucket, from the timeline periods of the alerts.
     */
    public List<GenericIntegerHistogramEntry> getActiveAlertsHistogram(UUID organizationId,
                                                                       UUID tenantId,
                                                                       TimeRange timeRange,
                                                                       Bucketing.BucketingConfiguration bucketing,
                                                                       Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + ", " + BUCKETS +
                                "SELECT b.bucket AS bucket, COUNT(DISTINCT t.detection_alert_id) AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN detection_alert_timeline AS t " +
                                "ON t.seen_from <= b.bucket + make_interval(secs => :bucket_size_s) " +
                                "AND t.seen_to >= b.bucket " +
                                "AND t.detection_alert_id IN (SELECT id FROM alerts) " +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC"),
                        organizationId, tenantId, timeRange, f)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("bucket_size_s", bucketing.bucketSizeMs() / 1000.0)
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    /*
     * How many matching alerts were raised for the first time in each bucket.
     */
    public List<GenericIntegerHistogramEntry> getNewAlertsHistogram(UUID organizationId,
                                                                    UUID tenantId,
                                                                    TimeRange timeRange,
                                                                    Bucketing.BucketingConfiguration bucketing,
                                                                    Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + ", " + BUCKETS +
                                "SELECT b.bucket AS bucket, COUNT(a.id) AS value " +
                                "FROM buckets AS b " +
                                "LEFT JOIN alerts AS a " +
                                "ON a.created_at >= b.bucket " +
                                "AND a.created_at < b.bucket + make_interval(secs => :bucket_size_s) " +
                                "GROUP BY b.bucket ORDER BY b.bucket DESC"),
                        organizationId, tenantId, timeRange, f)
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("bucket_size_s", bucketing.bucketSizeMs() / 1000.0)
                        .mapTo(GenericIntegerHistogramEntry.class)
                        .list()
        );
    }

    public enum AggregationKey {

        DETECTION_TYPE("alerts.detection_type"),
        SUBSYSTEM("alerts.subsystem"),
        AFFECTED_ADDRESS("host(alerts.affected_address)"),
        AFFECTED_MAC("alerts.affected_mac");

        private final String expression;

        AggregationKey(String expression) {
            this.expression = expression;
        }

    }

    public long countAggregationKeys(AggregationKey key,
                                     UUID organizationId,
                                     UUID tenantId,
                                     TimeRange timeRange,
                                     Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) + "SELECT COUNT(DISTINCT " + key.expression + ") " +
                                "FROM alerts WHERE " + key.expression + " IS NOT NULL"),
                        organizationId, tenantId, timeRange, f)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<StringNumberAggregationResult> aggregate(AggregationKey key,
                                                         UUID organizationId,
                                                         UUID tenantId,
                                                         TimeRange timeRange,
                                                         Filters filters,
                                                         TwoColumnHistogramOrderColumn orderColumn,
                                                         OrderDirection orderDirection,
                                                         int limit,
                                                         int offset) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                bindSearch(handle.createQuery(searchCte(f) +
                                "SELECT " + key.expression + " AS key, COUNT(*) AS value " +
                                "FROM alerts WHERE " + key.expression + " IS NOT NULL " +
                                "GROUP BY " + key.expression + " " +
                                "ORDER BY <order_column> <order_direction>, key ASC " +
                                "LIMIT :limit OFFSET :offset"),
                        organizationId, tenantId, timeRange, f)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(StringNumberAggregationResult.class)
                        .list()
        );
    }

    public int markAlertsAsResolved(UUID organizationId, UUID tenantId, TimeRange timeRange, Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate(searchCte(f) + "UPDATE detection_alerts SET is_resolved = true " +
                                "WHERE id IN (SELECT id FROM alerts)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(f.bindings())
                        .execute()
        );
    }

    public int deleteAlerts(UUID organizationId, UUID tenantId, TimeRange timeRange, Filters filters) {
        FilterSqlFragment f = FilterSql.generate(filters, new DetectionAlertFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate(searchCte(f) + "DELETE FROM detection_alerts " +
                                "WHERE id IN (SELECT id FROM alerts)")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindMap(f.bindings())
                        .execute()
        );
    }

    /*
     * The address or MAC an alert is about, from its attributes. Same key preference as the search query.
     */
    @Nullable
    public static String affectedAddress(List<DetectionAlertAttributeEntry> attributes) {
        return firstAttributeValue(attributes, AFFECTED_ADDRESS_KEYS);
    }

    @Nullable
    public static String affectedMac(List<DetectionAlertAttributeEntry> attributes) {
        return firstAttributeValue(attributes, AFFECTED_MAC_KEYS);
    }

    @Nullable
    private static String firstAttributeValue(List<DetectionAlertAttributeEntry> attributes, List<String> keys) {
        for (String key : keys) {
            for (DetectionAlertAttributeEntry attribute : attributes) {
                if (attribute.key().equals(key) && attribute.value() != null && !attribute.value().isEmpty()) {
                    return attribute.value();
                }
            }
        }

        return null;
    }

}
