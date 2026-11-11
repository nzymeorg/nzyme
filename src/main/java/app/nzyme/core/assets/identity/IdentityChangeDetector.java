package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.identity.db.AssetIdentityExclusionEntry;
import app.nzyme.core.assets.identity.db.IdentityChange;
import app.nzyme.core.assets.identity.db.IdentityChangeFindingEntry;
import app.nzyme.core.assets.profile.AssetAttributeDimension;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import app.nzyme.core.ethernet.l4.tcp.TcpSignature;

import java.util.*;
import java.util.stream.Collectors;

public class IdentityChangeDetector {

    private static final Logger LOG = LogManager.getLogger(IdentityChangeDetector.class);

    private static final int CHUNK_SIZE = 500;

    // Weights when the old value was replaced and when a value was added next to it. Zero is not scored.
    private record Weights(int replaced, int added) { }

    private static final Map<AssetAttributeDimension, Weights> DIMENSIONS = new LinkedHashMap<>() {{
        // The stack, not the fingerprint: a fingerprint also changes when the host moves to another link.
        put(AssetAttributeDimension.TCP_STACK, new Weights(50, 0));
        put(AssetAttributeDimension.TCP_SERVER_FINGERPRINT, new Weights(20, 0));
        put(AssetAttributeDimension.SOURCE_PORT_RANGE, new Weights(15, 0));
        put(AssetAttributeDimension.IP_ID_BEHAVIOR, new Weights(10, 0));
        put(AssetAttributeDimension.TCP_TIMESTAMP_RATE, new Weights(10, 0));
        put(AssetAttributeDimension.DHCP_FINGERPRINT, new Weights(15, 10));
        put(AssetAttributeDimension.DHCP_VENDOR_CLASS, new Weights(10, 5));
        put(AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER, new Weights(10, 0));
        put(AssetAttributeDimension.DHCP_PARAMETER_REQUEST_LIST, new Weights(10, 0));
        put(AssetAttributeDimension.JA4, new Weights(15, 0));
        put(AssetAttributeDimension.NTP_CLIENT_VERSION, new Weights(5, 0));
    }};

    private static final int NEW_SERVER_PROTOCOL_WEIGHT = 5;
    private static final int NEW_SERVER_PROTOCOL_CAP = 10;
    private static final int NEW_INBOUND_PORT_WEIGHT = 5;
    private static final int NEW_INBOUND_PORT_CAP = 10;
    private static final int NEW_JA4_THRESHOLD = 3;
    private static final int NEW_JA4_WEIGHT = 5;
    private static final int HOSTNAME_REPLACED_WEIGHT = 20;
    private static final int HOSTNAME_ADDED_WEIGHT = 5;

    private final NzymeNode nzyme;
    private final AssetIdentity identity;

    public IdentityChangeDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.identity = nzyme.getAssetsManager().identity();
    }

    private record Value(String value, DateTime firstSeen, DateTime lastSeen) { }
    private record Asset(long id, String mac, DateTime firstSeen) { }

    public void runForTenant(UUID organizationId, UUID tenantId, AssetIdentityConfiguration config) {
        if (!config.changeDetectionEnabled()) {
            LOG.debug("Identity change detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            return;
        }

        DateTime until = DateTime.now();
        DateTime since = until.minusHours(config.changeWindowHours());
        DateTime maximumFirstSeen = until.minusHours(config.changeMinimumAssetAgeHours());

        nzyme.getDatabase().useTransaction(handle -> {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "assets_identity_change:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                LOG.debug("Identity change detection for tenant [{}/{}] is running on another node. Skipping.",
                        organizationId, tenantId);
                return;
            }

            List<Asset> candidates = readCandidates(handle, organizationId, tenantId, since, maximumFirstSeen);
            if (candidates.isEmpty()) {
                return;
            }

            Map<String, IdentityChangeFindingEntry> activeFindings = Maps.newHashMap();
            for (IdentityChangeFindingEntry finding : identity.findActiveChangeFindingsOfTenant(handle, organizationId, tenantId, since)) {
                activeFindings.put(finding.mac(), finding);
            }

            Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                    .compileEnabled(handle, organizationId, tenantId);
            Map<Long, Long> exclusionMatches = Maps.newHashMap();

            for (List<Asset> chunk : Lists.partition(candidates, CHUNK_SIZE)) {
                Map<String, Map<String, List<Value>>> attributes = readAttributes(handle, organizationId, tenantId, chunk);
                Map<String, List<Value>> hostnames = readHostnames(handle, chunk);

                for (Asset asset : chunk) {
                    try {
                        List<IdentityChange> changes = score(
                                attributes.getOrDefault(asset.mac, Map.of()),
                                hostnames.getOrDefault(asset.mac, List.of()),
                                since
                        );

                        int score = changes.stream().mapToInt(IdentityChange::weight).sum();
                        if (changes.isEmpty() || score < config.changeScoreThreshold()) {
                            continue;
                        }

                        AssetIdentityExclusionEntry exclusion = AssetIdentityExclusions.findMatching(exclusions,
                                asset.mac, AssetIdentityType.IDENTITY_CHANGE);
                        if (exclusion != null) {
                            exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
                            continue;
                        }

                        String previousFingerprint = null;
                        String newFingerprint = null;
                        for (IdentityChange change : changes) {
                            if (change.signal().equals("TCP_STACK_REPLACED")) {
                                previousFingerprint = change.previousValue();
                                newFingerprint = change.newValue();
                            }
                        }

                        DateTime firstSeen = changes.stream().map(c -> DateTime.parse(c.seenAt()))
                                .min(Comparator.naturalOrder()).orElse(since);
                        DateTime lastSeen = changes.stream().map(c -> DateTime.parse(c.seenAt()))
                                .max(Comparator.naturalOrder()).orElse(until);

                        IdentityChangeFindingEntry finding = activeFindings.get(asset.mac);
                        if (finding == null) {
                            IdentityChangeFindingEntry created = identity.createChangeFinding(
                                    handle, organizationId, tenantId, asset.mac, score, changes,
                                    previousFingerprint, newFingerprint, firstSeen, lastSeen
                            );

                            if (config.changeAlertOnNew()) {
                                raiseNewFindingAlert(created, organizationId, tenantId);
                            }
                        } else {
                            identity.updateChangeFinding(handle, finding.id(), score, changes, previousFingerprint,
                                    newFingerprint, lastSeen);
                        }
                    } catch (Exception e) {
                        LOG.error("Could not process identity change of asset [{}] of tenant [{}/{}]. Skipping.",
                                asset.mac, organizationId, tenantId, e);
                    }
                }
            }

            for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
                identity.exclusions().recordMatches(handle, match.getKey(), match.getValue());
            }
        });
    }

    // An asset whose first ledger entry is recent has no known identity to change, however long it has been seen.
    private List<Asset> readCandidates(Handle handle, UUID organizationId, UUID tenantId, DateTime since,
                                       DateTime maximumFirstSeen) {
        List<String> dimensions = Lists.newArrayList();
        for (AssetAttributeDimension dimension : DIMENSIONS.keySet()) {
            dimensions.add(dimension.name());
        }
        dimensions.add(AssetAttributeDimension.PROTOCOL_SERVER.name());
        dimensions.add(AssetAttributeDimension.INBOUND_PORT.name());

        return handle.createQuery("SELECT a.id, a.mac, a.first_seen FROM assets AS a " +
                        "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                        "AND a.first_seen <= :maximum_first_seen AND a.last_seen >= :since " +
                        "AND EXISTS (SELECT 1 FROM assets_attributes AS at WHERE at.organization_id = a.organization_id " +
                        "AND at.tenant_id = a.tenant_id AND at.mac = a.mac AND at.first_seen <= :maximum_first_seen) " +
                        "AND (EXISTS (SELECT 1 FROM assets_attributes AS at WHERE at.organization_id = a.organization_id " +
                        "AND at.tenant_id = a.tenant_id AND at.mac = a.mac AND at.dimension IN (<dimensions>) " +
                        "AND at.first_seen >= :since) " +
                        "OR EXISTS (SELECT 1 FROM assets_hostnames AS h WHERE h.asset_id = a.id AND h.first_seen >= :since))")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("maximum_first_seen", maximumFirstSeen)
                .bind("since", since)
                .bindList("dimensions", dimensions)
                .map((rs, ctx) -> new Asset(rs.getLong("id"), rs.getString("mac"),
                        new DateTime(rs.getTimestamp("first_seen"))))
                .list()
                .stream()
                .filter(a -> AssetProfileBatch.isUsableMac(a.mac))
                .collect(Collectors.toList());
    }

    private Map<String, Map<String, List<Value>>> readAttributes(Handle handle, UUID organizationId, UUID tenantId,
                                                                 List<Asset> assets) {
        List<String> dimensions = Lists.newArrayList();
        for (AssetAttributeDimension dimension : DIMENSIONS.keySet()) {
            dimensions.add(dimension.name());
        }
        dimensions.add(AssetAttributeDimension.PROTOCOL_SERVER.name());
        dimensions.add(AssetAttributeDimension.INBOUND_PORT.name());

        Map<String, Map<String, List<Value>>> result = Maps.newHashMap();
        handle.createQuery("SELECT mac, dimension, value, first_seen, last_seen FROM assets_attributes " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND mac IN (<macs>) AND dimension IN (<dimensions>)")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bindList("macs", assets.stream().map(Asset::mac).collect(Collectors.toList()))
                .bindList("dimensions", dimensions)
                .map((rs, ctx) -> Map.entry(rs.getString("mac"), Map.entry(rs.getString("dimension"),
                        new Value(rs.getString("value"), new DateTime(rs.getTimestamp("first_seen")),
                                new DateTime(rs.getTimestamp("last_seen"))))))
                .forEach(row -> result.computeIfAbsent(row.getKey(), k -> Maps.newHashMap())
                        .computeIfAbsent(row.getValue().getKey(), k -> Lists.newArrayList())
                        .add(row.getValue().getValue()));

        return result;
    }

    private Map<String, List<Value>> readHostnames(Handle handle, List<Asset> assets) {
        Map<Long, String> macsById = Maps.newHashMap();
        for (Asset asset : assets) {
            macsById.put(asset.id, asset.mac);
        }

        Map<String, List<Value>> result = Maps.newHashMap();
        handle.createQuery("SELECT asset_id, hostname, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen " +
                        "FROM assets_hostnames WHERE asset_id IN (<ids>) GROUP BY asset_id, hostname")
                .bindList("ids", Lists.newArrayList(macsById.keySet()))
                .map((rs, ctx) -> Map.entry(rs.getLong("asset_id"), new Value(rs.getString("hostname"),
                        new DateTime(rs.getTimestamp("first_seen")), new DateTime(rs.getTimestamp("last_seen")))))
                .forEach(row -> {
                    String mac = macsById.get(row.getKey());
                    if (mac != null) {
                        result.computeIfAbsent(mac, k -> Lists.newArrayList()).add(row.getValue());
                    }
                });

        return result;
    }

    private static List<IdentityChange> score(Map<String, List<Value>> attributes, List<Value> hostnames, DateTime since) {
        List<IdentityChange> changes = Lists.newArrayList();

        for (Map.Entry<AssetAttributeDimension, Weights> entry : DIMENSIONS.entrySet()) {
            List<Value> values = attributes.get(entry.getKey().name());
            if (values == null) {
                continue;
            }

            if (entry.getKey() == AssetAttributeDimension.TCP_STACK) {
                values = collapseStacks(values);
            }

            IdentityChange change = replacement(entry.getKey().name(), values, since, entry.getValue());
            if (change != null) {
                changes.add(change);
            }
        }

        List<Value> ja4 = attributes.get(AssetAttributeDimension.JA4.name());
        if (ja4 != null && changes.stream().noneMatch(c -> c.signal().equals("JA4_REPLACED"))) {
            List<Value> newValues = newValues(ja4, since);
            List<Value> oldValues = oldValues(ja4, since);
            if (!oldValues.isEmpty() && newValues.size() >= NEW_JA4_THRESHOLD) {
                changes.add(new IdentityChange("JA4_ADDED", NEW_JA4_WEIGHT, null,
                        join(newValues), earliest(newValues).toString()));
            }
        }

        added("PROTOCOL_SERVER_ADDED", attributes.get(AssetAttributeDimension.PROTOCOL_SERVER.name()), since,
                NEW_SERVER_PROTOCOL_WEIGHT, NEW_SERVER_PROTOCOL_CAP, changes);
        added("INBOUND_PORT_ADDED", attributes.get(AssetAttributeDimension.INBOUND_PORT.name()), since,
                NEW_INBOUND_PORT_WEIGHT, NEW_INBOUND_PORT_CAP, changes);

        IdentityChange hostname = replacement("HOSTNAME", hostnames, since,
                new Weights(HOSTNAME_REPLACED_WEIGHT, HOSTNAME_ADDED_WEIGHT));
        if (hostname != null) {
            changes.add(hostname);
        }

        return changes;
    }

    // Merged value keeps the stack string of the oldest entry.
    static List<Value> collapseStacks(List<Value> values) {
        Map<String, Value> merged = new LinkedHashMap<>();
        for (Value value : values) {
            merged.merge(TcpSignature.comparisonKey(value.value), value, IdentityChangeDetector::mergeStacks);
        }

        Map<String, String> plain = Maps.newHashMap();
        for (String key : merged.keySet()) {
            if (!TcpSignature.hasTimestamps(key)) {
                plain.putIfAbsent(TcpSignature.timestamplessKey(key), key);
            }
        }

        for (String key : Lists.newArrayList(merged.keySet())) {
            String target = TcpSignature.hasTimestamps(key) ? plain.get(TcpSignature.timestamplessKey(key)) : null;
            if (target != null) {
                merged.merge(target, merged.remove(key), IdentityChangeDetector::mergeStacks);
            }
        }

        return Lists.newArrayList(merged.values());
    }

    private static Value mergeStacks(Value a, Value b) {
        return new Value(
                a.firstSeen.isAfter(b.firstSeen) ? b.value : a.value,
                a.firstSeen.isBefore(b.firstSeen) ? a.firstSeen : b.firstSeen,
                a.lastSeen.isAfter(b.lastSeen) ? a.lastSeen : b.lastSeen
        );
    }

    @Nullable
    private static IdentityChange replacement(String signal, List<Value> values, DateTime since, Weights weights) {
        List<Value> newValues = newValues(values, since);
        List<Value> oldValues = oldValues(values, since);

        if (newValues.isEmpty() || oldValues.isEmpty()) {
            return null;
        }

        DateTime newFirst = earliest(newValues);
        boolean oldStillSeen = oldValues.stream().anyMatch(v -> !v.lastSeen.isBefore(newFirst));

        if (!oldStillSeen && weights.replaced > 0) {
            return new IdentityChange(signal + "_REPLACED", weights.replaced, join(oldValues), join(newValues),
                    newFirst.toString());
        }

        if (oldStillSeen && weights.added > 0) {
            return new IdentityChange(signal + "_ADDED", weights.added, join(oldValues), join(newValues),
                    newFirst.toString());
        }

        return null;
    }

    private static void added(String signal, @Nullable List<Value> values, DateTime since, int weight, int cap,
                              List<IdentityChange> changes) {
        if (values == null) {
            return;
        }

        List<Value> newValues = newValues(values, since);
        List<Value> oldValues = oldValues(values, since);
        if (newValues.isEmpty() || oldValues.isEmpty()) {
            return;
        }

        changes.add(new IdentityChange(signal, Math.min(cap, weight * newValues.size()), null, join(newValues),
                earliest(newValues).toString()));
    }

    private static List<Value> newValues(List<Value> values, DateTime since) {
        return values.stream().filter(v -> !v.firstSeen.isBefore(since)).collect(Collectors.toList());
    }

    private static List<Value> oldValues(List<Value> values, DateTime since) {
        return values.stream().filter(v -> v.firstSeen.isBefore(since)).collect(Collectors.toList());
    }

    private static DateTime earliest(List<Value> values) {
        return values.stream().map(Value::firstSeen).min(Comparator.naturalOrder()).orElseThrow();
    }

    private static String join(List<Value> values) {
        return values.stream().map(Value::value).sorted().limit(10).collect(Collectors.joining(", "));
    }

    private void raiseNewFindingAlert(IdentityChangeFindingEntry finding, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("finding_uuid", finding.uuid().toString());
        attributes.put("mac", finding.mac());
        attributes.put("score", String.valueOf(finding.score()));
        attributes.put("signals", finding.changes().stream().map(IdentityChange::signal).collect(Collectors.joining(", ")));
        if (finding.previousFingerprint() != null) {
            attributes.put("previous_fingerprint", finding.previousFingerprint());
        }
        if (finding.newFingerprint() != null) {
            attributes.put("new_fingerprint", finding.newFingerprint());
        }

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.ASSETS_IDENTITY_CHANGED,
                Subsystem.ETHERNET,
                "Asset \"" + finding.mac() + "\" changed its identity (score " + finding.score() + ").",
                attributes,
                Set.of("finding_uuid")
        );
    }

}
