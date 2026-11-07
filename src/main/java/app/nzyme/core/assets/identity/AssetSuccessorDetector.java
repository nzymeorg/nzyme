package app.nzyme.core.assets.identity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.identity.db.AssetIdentityExclusionEntry;
import app.nzyme.core.assets.identity.db.AssetLinkEntry;
import app.nzyme.core.assets.identity.db.AssetLinkEvidence;
import app.nzyme.core.assets.profile.AssetAttributeDimension;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.*;
import java.util.stream.Collectors;

public class AssetSuccessorDetector {

    private static final Logger LOG = LogManager.getLogger(AssetSuccessorDetector.class);

    private static final int BATCH_SIZE = 500;
    private static final int MAXIMUM_BATCHES_PER_RUN = 10;

    // Values shared by more silent assets than this are too common to find candidates with.
    private static final int MAXIMUM_CANDIDATES_PER_KEY = 200;

    // Per asset: hostnames, addresses and values per dimension loaded into a profile, and values kept on evidence.
    private static final int MAXIMUM_PROFILE_VALUES = 100;
    private static final int MAXIMUM_EVIDENCE_VALUES = 10;

    // Minutes after first seen at which an asset is checked again. The end of the lookback is always added.
    private static final List<Integer> RECHECK_AGES_MINUTES = List.of(360, 1440);

    private static final List<AssetAttributeDimension> CANDIDATE_DIMENSIONS = List.of(
            AssetAttributeDimension.DHCP_FINGERPRINT,
            AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER,
            AssetAttributeDimension.TCP_STACK,
            AssetAttributeDimension.DHCP_PARAMETER_REQUEST_LIST,
            AssetAttributeDimension.JA4,
            AssetAttributeDimension.TCP_SERVER_FINGERPRINT
    );

    private static final List<AssetAttributeDimension> SPREAD_DIMENSIONS = List.of(
            AssetAttributeDimension.DHCP_FINGERPRINT,
            AssetAttributeDimension.DHCP_PARAMETER_REQUEST_LIST,
            AssetAttributeDimension.TCP_STACK,
            AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER
    );

    private static final List<AssetAttributeDimension> DIMENSIONS = List.of(
            AssetAttributeDimension.DHCP_FINGERPRINT,
            AssetAttributeDimension.DHCP_VENDOR_CLASS,
            AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER,
            AssetAttributeDimension.DHCP_PARAMETER_REQUEST_LIST,
            AssetAttributeDimension.TCP_STACK,
            AssetAttributeDimension.TCP_SERVER_FINGERPRINT,
            AssetAttributeDimension.JA4,
            AssetAttributeDimension.INBOUND_PORT,
            AssetAttributeDimension.NTP_CLIENT_VERSION,
            AssetAttributeDimension.SOURCE_PORT_RANGE,
            AssetAttributeDimension.TCP_TIMESTAMP_RATE
    );

    private final NzymeNode nzyme;
    private final AssetIdentity identity;

    public AssetSuccessorDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.identity = nzyme.getAssetsManager().identity();
    }

    private record Asset(long id, UUID uuid, String mac, DateTime firstSeen, DateTime lastSeen) { }

    private record Check(long id, Asset asset) { }

    private static final class Profile {
        final Asset asset;
        final Set<String> hostnames = new HashSet<>();
        final Set<String> ipAddresses = new HashSet<>();
        final Map<String, Set<String>> attributes = new HashMap<>();

        Profile(Asset asset) {
            this.asset = asset;
        }

        Set<String> values(AssetAttributeDimension dimension) {
            return attributes.getOrDefault(dimension.name(), Set.of());
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId, AssetIdentityConfiguration config) {
        if (!config.successorDetectionEnabled()) {
            LOG.debug("MAC change detection disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            // Pending checks of a disabled tenant must not count as backlog.
            nzyme.getDatabase().useHandle(handle -> deleteAllChecks(handle, organizationId, tenantId));
            return;
        }

        DateTime now = DateTime.now();
        DateTime lookbackSince = now.minusHours(config.successorLookbackHours());
        DateTime quietBefore = now.minusMinutes(config.successorQuietMinutes());

        nzyme.getDatabase().useTransaction(handle -> {
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "assets_identity_successor:" + organizationId + ":" + tenantId)
                    .mapTo(Boolean.class)
                    .one();

            if (!locked) {
                LOG.debug("MAC change detection for tenant [{}/{}] is running on another node. Skipping.",
                        organizationId, tenantId);
                return;
            }

            identity.markReappearedPredecessorsAsAlternate(handle, organizationId, tenantId,
                    config.successorQuietMinutes());

            cleanChecks(handle, organizationId, tenantId, lookbackSince);
            enqueueChecks(handle, organizationId, tenantId, lookbackSince, config.successorQuietMinutes());

            Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions = identity.exclusions()
                    .compileEnabled(handle, organizationId, tenantId);
            Map<Long, Long> exclusionMatches = Maps.newHashMap();

            for (int batch = 0; batch < MAXIMUM_BATCHES_PER_RUN; batch++) {
                List<Check> due = readDueChecks(handle, organizationId, tenantId, now);
                if (due.isEmpty()) {
                    break;
                }

                processBatch(handle, organizationId, tenantId, config, now, quietBefore, due, exclusions,
                        exclusionMatches);

                if (due.size() < BATCH_SIZE) {
                    break;
                }
            }

            for (Map.Entry<Long, Long> match : exclusionMatches.entrySet()) {
                identity.exclusions().recordMatches(handle, match.getKey(), match.getValue());
            }
        });
    }

    private void processBatch(Handle handle, UUID organizationId, UUID tenantId, AssetIdentityConfiguration config,
                              DateTime now, DateTime quietBefore, List<Check> due,
                              Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions,
                              Map<Long, Long> exclusionMatches) {
        Map<Long, DateTime> advances = Maps.newHashMap();

        List<Asset> successors = Lists.newArrayList();
        for (Check check : due) {
            if (AssetProfileBatch.isUsableMac(check.asset.mac)) {
                successors.add(check.asset);
            } else {
                advances.put(check.id, null);
            }
        }

        if (!successors.isEmpty()) {
            Map<String, Profile> successorProfiles = readProfiles(handle, organizationId, tenantId, successors);

            DateTime earliestFirstSeen = successors.stream().map(Asset::firstSeen).min(DateTime::compareTo).get();
            DateTime latestFirstSeen = successors.stream().map(Asset::firstSeen).max(DateTime::compareTo).get();
            DateTime silentAfter = earliestFirstSeen.minusHours(config.successorLookbackHours());
            DateTime silentBefore = latestFirstSeen.plusMinutes(config.successorQuietMinutes());
            if (silentBefore.isAfter(quietBefore)) {
                silentBefore = quietBefore;
            }

            Map<String, List<Profile>> index = readCandidatePredecessors(handle, organizationId, tenantId,
                    successorProfiles.values(), silentAfter, silentBefore);

            Map<String, Integer> hostnameSpread = index.isEmpty() ? Map.of()
                    : readHostnameSpread(handle, organizationId, tenantId, successorProfiles.values());
            Map<String, Integer> attributeSpread = index.isEmpty() ? Map.of()
                    : readAttributeSpread(handle, organizationId, tenantId, successorProfiles.values());

            for (Check check : due) {
                Asset newAsset = check.asset;
                Profile successor = successorProfiles.get(newAsset.mac);
                if (successor == null) {
                    continue;
                }

                boolean linked = false;
                try {
                    linked = evaluate(handle, organizationId, tenantId, config, newAsset, successor, index,
                            hostnameSpread, attributeSpread, exclusions, exclusionMatches);
                } catch (Exception e) {
                    LOG.error("Could not evaluate new asset [{}] of tenant [{}/{}] for a MAC change. Skipping.",
                            newAsset.mac, organizationId, tenantId, e);
                }

                // Failed evaluations are retried later so one broken asset never blocks the queue.
                advances.put(check.id, linked ? null : nextCheck(newAsset.firstSeen, now, config));
            }
        }

        advanceChecks(handle, advances);
    }

    private boolean evaluate(Handle handle, UUID organizationId, UUID tenantId, AssetIdentityConfiguration config,
                             Asset newAsset, Profile successor, Map<String, List<Profile>> index,
                             Map<String, Integer> hostnameSpread, Map<String, Integer> attributeSpread,
                             Map<AssetIdentityExclusionEntry, AssetIdentityExclusionMatcher> exclusions,
                             Map<Long, Long> exclusionMatches) {
        Profile best = null;
        int bestConfidence = 0;
        List<AssetLinkEvidence> bestEvidence = null;

        for (Profile predecessor : candidates(successor, index)) {
            if (predecessor.asset.mac.equals(newAsset.mac)) {
                continue;
            }

            if (predecessor.asset.lastSeen.isAfter(newAsset.firstSeen.plusMinutes(config.successorQuietMinutes()))
                    || predecessor.asset.lastSeen.isBefore(newAsset.firstSeen.minusHours(config.successorLookbackHours()))) {
                continue;
            }

            List<AssetLinkEvidence> evidence = score(predecessor, successor, hostnameSpread, attributeSpread);
            int confidence = Math.max(0, Math.min(100, evidence.stream().mapToInt(AssetLinkEvidence::weight).sum()));

            if (confidence > bestConfidence) {
                best = predecessor;
                bestConfidence = confidence;
                bestEvidence = evidence;
            }
        }

        if (best == null || bestConfidence < config.successorMinimumConfidence()) {
            return false;
        }

        AssetIdentityExclusionEntry exclusion = AssetIdentityExclusions.findMatching(exclusions,
                newAsset.mac, AssetIdentityType.MAC_CHANGE);
        if (exclusion == null) {
            exclusion = AssetIdentityExclusions.findMatching(exclusions, best.asset.mac,
                    AssetIdentityType.MAC_CHANGE);
        }
        if (exclusion != null) {
            exclusionMatches.merge(exclusion.id(), 1L, Long::sum);
            // Excluded pairs are not evaluated again.
            return true;
        }

        boolean existed = linkExists(handle, organizationId, tenantId, best.asset.mac, newAsset.mac);
        Optional<AssetLinkEntry> link = identity.upsertLink(
                handle,
                organizationId,
                tenantId,
                best.asset.mac,
                newAsset.mac,
                AssetIdentity.LINK_KIND_SUCCESSOR,
                bestConfidence,
                bestEvidence,
                best.asset.lastSeen,
                newAsset.firstSeen
        );

        if (link.isPresent() && !existed && config.successorAlertOnNew()) {
            raiseNewLinkAlert(link.get(), organizationId, tenantId);
        }

        return true;
    }

    // Null when the schedule is exhausted.
    static DateTime nextCheck(DateTime firstSeen, DateTime now, AssetIdentityConfiguration config) {
        int lookbackMinutes = config.successorLookbackHours() * 60;

        TreeSet<Integer> ages = new TreeSet<>(RECHECK_AGES_MINUTES);
        ages.add(lookbackMinutes);

        for (int age : ages) {
            if (age > lookbackMinutes) {
                break;
            }

            DateTime at = firstSeen.plusMinutes(age);
            if (at.isAfter(now)) {
                return at;
            }
        }

        return null;
    }

    private void enqueueChecks(Handle handle, UUID organizationId, UUID tenantId, DateTime lookbackSince,
                               int quietMinutes) {
        handle.createUpdate("INSERT INTO assets_identity_successor_checks(organization_id, tenant_id, mac, " +
                        "asset_first_seen, checks_done, next_check_at, created_at, updated_at) " +
                        "SELECT a.organization_id, a.tenant_id, a.mac, a.first_seen, 0, " +
                        "a.first_seen + make_interval(mins => :quiet_minutes), NOW(), NOW() FROM assets AS a " +
                        "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                        "AND a.first_seen >= :lookback_since " +
                        "AND NOT EXISTS (SELECT 1 FROM assets_identity_successor_checks AS c " +
                        "WHERE c.organization_id = a.organization_id AND c.tenant_id = a.tenant_id AND c.mac = a.mac) " +
                        "AND NOT EXISTS (SELECT 1 FROM assets_links AS l WHERE l.organization_id = a.organization_id " +
                        "AND l.tenant_id = a.tenant_id AND l.successor_mac = a.mac) " +
                        "ON CONFLICT (organization_id, tenant_id, mac) DO NOTHING")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("lookback_since", lookbackSince)
                .bind("quiet_minutes", quietMinutes)
                .execute();
    }

    // Finished checks inside the lookback stay so the asset is not enqueued again.
    private void cleanChecks(Handle handle, UUID organizationId, UUID tenantId, DateTime lookbackSince) {
        handle.createUpdate("DELETE FROM assets_identity_successor_checks AS c " +
                        "WHERE c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                        "AND (c.asset_first_seen < :lookback_since OR NOT EXISTS (SELECT 1 FROM assets AS a " +
                        "WHERE a.organization_id = c.organization_id AND a.tenant_id = c.tenant_id AND a.mac = c.mac))")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("lookback_since", lookbackSince)
                .execute();
    }

    private void deleteAllChecks(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createUpdate("DELETE FROM assets_identity_successor_checks " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    private List<Check> readDueChecks(Handle handle, UUID organizationId, UUID tenantId, DateTime now) {
        return handle.createQuery("SELECT c.id AS check_id, a.id, a.uuid, a.mac, a.first_seen, a.last_seen " +
                        "FROM assets_identity_successor_checks AS c " +
                        "JOIN assets AS a ON a.organization_id = c.organization_id AND a.tenant_id = c.tenant_id " +
                        "AND a.mac = c.mac " +
                        "WHERE c.organization_id = :organization_id AND c.tenant_id = :tenant_id " +
                        "AND c.next_check_at IS NOT NULL AND c.next_check_at <= :now " +
                        "ORDER BY c.next_check_at ASC LIMIT :limit")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("now", now)
                .bind("limit", BATCH_SIZE)
                .map((rs, ctx) -> new Check(rs.getLong("check_id"), new Asset(rs.getLong("id"),
                        UUID.fromString(rs.getString("uuid")), rs.getString("mac"),
                        new DateTime(rs.getTimestamp("first_seen")), new DateTime(rs.getTimestamp("last_seen")))))
                .list();
    }

    // A null next check marks the asset as finished.
    private void advanceChecks(Handle handle, Map<Long, DateTime> advances) {
        if (advances.isEmpty()) {
            return;
        }

        PreparedBatch batch = handle.prepareBatch("UPDATE assets_identity_successor_checks " +
                "SET checks_done = checks_done + 1, next_check_at = :next_check_at, updated_at = NOW() WHERE id = :id");
        for (Map.Entry<Long, DateTime> advance : advances.entrySet()) {
            batch.bind("id", advance.getKey())
                    .bind("next_check_at", advance.getValue())
                    .add();
        }
        batch.execute();
    }

    private Map<String, Profile> readProfiles(Handle handle, UUID organizationId, UUID tenantId,
                                              Collection<Asset> assets) {
        Map<String, Profile> profiles = Maps.newHashMap();
        Map<Long, Profile> byId = Maps.newHashMap();
        for (Asset asset : assets) {
            Profile p = profiles.computeIfAbsent(asset.mac, k -> new Profile(asset));
            byId.put(asset.id, p);
        }

        for (List<Long> ids : Lists.partition(Lists.newArrayList(byId.keySet()), 1000)) {
            handle.createQuery("SELECT asset_id, hostname FROM (SELECT asset_id, hostname, ROW_NUMBER() OVER " +
                            "(PARTITION BY asset_id ORDER BY last_seen DESC) AS n FROM assets_hostnames " +
                            "WHERE asset_id IN (<ids>)) AS h WHERE h.n <= :cap")
                    .bindList("ids", ids)
                    .bind("cap", MAXIMUM_PROFILE_VALUES)
                    .map((rs, ctx) -> Map.entry(rs.getLong("asset_id"), rs.getString("hostname")))
                    .forEach(row -> {
                        Profile p = byId.get(row.getKey());
                        if (p != null) {
                            p.hostnames.add(row.getValue().toLowerCase());
                        }
                    });

            handle.createQuery("SELECT asset_id, address FROM (SELECT asset_id, host(address) AS address, " +
                            "ROW_NUMBER() OVER (PARTITION BY asset_id ORDER BY last_seen DESC) AS n " +
                            "FROM assets_ip_addresses WHERE asset_id IN (<ids>)) AS ip WHERE ip.n <= :cap")
                    .bindList("ids", ids)
                    .bind("cap", MAXIMUM_PROFILE_VALUES)
                    .map((rs, ctx) -> Map.entry(rs.getLong("asset_id"), rs.getString("address")))
                    .forEach(row -> {
                        Profile p = byId.get(row.getKey());
                        if (p != null && isUsableAddress(row.getValue())) {
                            p.ipAddresses.add(row.getValue());
                        }
                    });
        }

        List<String> dimensions = DIMENSIONS.stream().map(Enum::name).collect(Collectors.toList());
        for (List<String> macs : Lists.partition(Lists.newArrayList(profiles.keySet()), 1000)) {
            handle.createQuery("SELECT mac, dimension, value FROM (SELECT mac, dimension, value, ROW_NUMBER() OVER " +
                            "(PARTITION BY mac, dimension ORDER BY last_seen DESC) AS n FROM assets_attributes " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND mac IN (<macs>) AND dimension IN (<dimensions>)) AS at WHERE at.n <= :cap")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bindList("macs", macs)
                    .bindList("dimensions", dimensions)
                    .bind("cap", MAXIMUM_PROFILE_VALUES)
                    .map((rs, ctx) -> new String[]{rs.getString("mac"), rs.getString("dimension"), rs.getString("value")})
                    .forEach(row -> {
                        Profile p = profiles.get(row[0]);
                        if (p != null) {
                            p.attributes.computeIfAbsent(row[1], k -> new HashSet<>()).add(row[2]);
                        }
                    });
        }

        return profiles;
    }

    private Map<String, List<Profile>> readCandidatePredecessors(Handle handle, UUID organizationId, UUID tenantId,
                                                                 Collection<Profile> successors,
                                                                 DateTime silentAfter, DateTime silentBefore) {
        Set<String> hostnames = new HashSet<>();
        Set<String> addresses = new HashSet<>();
        Map<AssetAttributeDimension, Set<String>> values = new EnumMap<>(AssetAttributeDimension.class);
        for (Profile successor : successors) {
            hostnames.addAll(successor.hostnames);
            addresses.addAll(successor.ipAddresses);
            for (AssetAttributeDimension dimension : CANDIDATE_DIMENSIONS) {
                values.computeIfAbsent(dimension, k -> new HashSet<>()).addAll(successor.values(dimension));
            }
        }

        Map<String, Asset> candidates = Maps.newHashMap();
        Map<String, Set<String>> keyMacs = Maps.newHashMap();

        collectCandidates(handle, organizationId, tenantId, silentAfter, silentBefore, "h|", hostnames,
                "SELECT DISTINCT LOWER(h.hostname) AS key, a.id, a.uuid, a.mac, a.first_seen, a.last_seen " +
                        "FROM assets_hostnames AS h JOIN assets AS a ON a.id = h.asset_id " +
                        "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                        "AND a.last_seen >= :silent_after AND a.last_seen <= :silent_before " +
                        "AND LOWER(h.hostname) IN (<keys>)",
                null, candidates, keyMacs);

        collectCandidates(handle, organizationId, tenantId, silentAfter, silentBefore, "i|", addresses,
                "SELECT DISTINCT host(ip.address) AS key, a.id, a.uuid, a.mac, a.first_seen, a.last_seen " +
                        "FROM assets_ip_addresses AS ip JOIN assets AS a ON a.id = ip.asset_id " +
                        "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                        "AND a.last_seen >= :silent_after AND a.last_seen <= :silent_before " +
                        "AND host(ip.address) IN (<keys>)",
                null, candidates, keyMacs);

        for (AssetAttributeDimension dimension : CANDIDATE_DIMENSIONS) {
            collectCandidates(handle, organizationId, tenantId, silentAfter, silentBefore, dimension.name() + "|",
                    values.getOrDefault(dimension, Set.of()),
                    "SELECT DISTINCT at.value AS key, a.id, a.uuid, a.mac, a.first_seen, a.last_seen " +
                            "FROM assets_attributes AS at JOIN assets AS a ON a.organization_id = at.organization_id " +
                            "AND a.tenant_id = at.tenant_id AND a.mac = at.mac " +
                            "WHERE at.organization_id = :organization_id AND at.tenant_id = :tenant_id " +
                            "AND at.dimension = :dimension AND at.value IN (<keys>) " +
                            "AND a.last_seen >= :silent_after AND a.last_seen <= :silent_before",
                    dimension.name(), candidates, keyMacs);
        }

        if (candidates.isEmpty()) {
            return Map.of();
        }

        Map<String, Profile> profiles = readProfiles(handle, organizationId, tenantId, candidates.values());

        Map<String, List<Profile>> index = Maps.newHashMap();
        for (Map.Entry<String, Set<String>> entry : keyMacs.entrySet()) {
            List<Profile> matches = Lists.newArrayList();
            for (String mac : entry.getValue()) {
                Profile p = profiles.get(mac);
                if (p != null) {
                    matches.add(p);
                }
            }
            index.put(entry.getKey(), matches);
        }

        return index;
    }

    private void collectCandidates(Handle handle, UUID organizationId, UUID tenantId, DateTime silentAfter,
                                   DateTime silentBefore, String keyPrefix, Set<String> keys, String innerSql,
                                   String dimension, Map<String, Asset> candidates, Map<String, Set<String>> keyMacs) {
        if (keys.isEmpty()) {
            return;
        }

        String sql = "SELECT key, id, uuid, mac, first_seen, last_seen FROM (SELECT x.*, COUNT(*) OVER " +
                "(PARTITION BY x.key) AS n FROM (" + innerSql + ") AS x) AS y WHERE y.n <= :maximum";

        for (List<String> chunk : Lists.partition(Lists.newArrayList(keys), 1000)) {
            var query = handle.createQuery(sql)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("silent_after", silentAfter)
                    .bind("silent_before", silentBefore)
                    .bind("maximum", MAXIMUM_CANDIDATES_PER_KEY)
                    .bindList("keys", chunk);
            if (dimension != null) {
                query.bind("dimension", dimension);
            }

            query.map((rs, ctx) -> Map.entry(rs.getString("key"), new Asset(rs.getLong("id"),
                            UUID.fromString(rs.getString("uuid")), rs.getString("mac"),
                            new DateTime(rs.getTimestamp("first_seen")), new DateTime(rs.getTimestamp("last_seen")))))
                    .forEach(row -> {
                        Asset asset = row.getValue();
                        if (!AssetProfileBatch.isUsableMac(asset.mac)) {
                            return;
                        }
                        candidates.putIfAbsent(asset.mac, asset);
                        keyMacs.computeIfAbsent(keyPrefix + row.getKey(), k -> new HashSet<>()).add(asset.mac);
                    });
        }
    }

    private Map<String, Integer> readHostnameSpread(Handle handle, UUID organizationId, UUID tenantId,
                                                    Collection<Profile> successors) {
        Set<String> hostnames = new HashSet<>();
        for (Profile p : successors) {
            hostnames.addAll(p.hostnames);
        }

        Map<String, Integer> result = Maps.newHashMap();
        for (List<String> chunk : Lists.partition(Lists.newArrayList(hostnames), 1000)) {
            handle.createQuery("SELECT LOWER(h.hostname) AS hostname, COUNT(DISTINCT h.asset_id) AS c " +
                            "FROM assets_hostnames AS h JOIN assets AS a ON a.id = h.asset_id " +
                            "WHERE a.organization_id = :organization_id AND a.tenant_id = :tenant_id " +
                            "AND LOWER(h.hostname) IN (<hostnames>) GROUP BY LOWER(h.hostname)")
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bindList("hostnames", chunk)
                    .map((rs, ctx) -> Map.entry(rs.getString("hostname"), rs.getInt("c")))
                    .forEach(row -> result.put(row.getKey(), row.getValue()));
        }

        return result;
    }

    private Map<String, Integer> readAttributeSpread(Handle handle, UUID organizationId, UUID tenantId,
                                                     Collection<Profile> successors) {
        Map<String, Integer> result = Maps.newHashMap();

        for (AssetAttributeDimension dimension : SPREAD_DIMENSIONS) {
            Set<String> values = new HashSet<>();
            for (Profile p : successors) {
                values.addAll(p.values(dimension));
            }

            for (List<String> chunk : Lists.partition(Lists.newArrayList(values), 1000)) {
                handle.createQuery("SELECT value, COUNT(DISTINCT mac) AS c FROM assets_attributes " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND dimension = :dimension AND value IN (<values>) GROUP BY value")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("dimension", dimension.name())
                        .bindList("values", chunk)
                        .map((rs, ctx) -> Map.entry(rs.getString("value"), rs.getInt("c")))
                        .forEach(row -> result.put(dimension.name() + "|" + row.getKey(), row.getValue()));
            }
        }

        return result;
    }

    private static Set<String> keysOf(Profile p) {
        Set<String> keys = new HashSet<>();
        for (String hostname : p.hostnames) {
            keys.add("h|" + hostname);
        }
        for (String address : p.ipAddresses) {
            keys.add("i|" + address);
        }
        for (AssetAttributeDimension dimension : CANDIDATE_DIMENSIONS) {
            for (String value : p.values(dimension)) {
                keys.add(dimension.name() + "|" + value);
            }
        }

        return keys;
    }

    private static Collection<Profile> candidates(Profile successor, Map<String, List<Profile>> index) {
        Set<Profile> result = Sets.newIdentityHashSet();
        for (String key : keysOf(successor)) {
            List<Profile> matches = index.get(key);
            if (matches != null) {
                result.addAll(matches);
            }
        }

        return result;
    }

    private static List<AssetLinkEvidence> score(Profile predecessor, Profile successor,
                                                 Map<String, Integer> hostnameSpread,
                                                 Map<String, Integer> attributeSpread) {
        List<AssetLinkEvidence> evidence = Lists.newArrayList();

        int hostnameWeight = 0;
        List<String> sharedHostnames = Lists.newArrayList();
        for (String hostname : Sets.intersection(predecessor.hostnames, successor.hostnames)) {
            sharedHostnames.add(hostname);
            hostnameWeight += (int) Math.round(40 * specificity(hostnameSpread.getOrDefault(hostname, 2)));
        }
        if (!sharedHostnames.isEmpty()) {
            evidence.add(new AssetLinkEvidence("HOSTNAME", Math.min(40, hostnameWeight),
                    sample(sharedHostnames)));
        }

        Set<String> sharedAddresses = Sets.intersection(predecessor.ipAddresses, successor.ipAddresses);
        if (!sharedAddresses.isEmpty()) {
            evidence.add(new AssetLinkEvidence("IP_ADDRESS", 30, sample(sharedAddresses)));
        }

        Set<String> clientIds = Sets.intersection(predecessor.values(AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER),
                successor.values(AssetAttributeDimension.DHCP_CLIENT_IDENTIFIER));
        for (String clientId : clientIds) {
            // A client identifier that is just the MAC address changes with it and proves nothing.
            if (!clientId.toLowerCase().endsWith(predecessor.asset.mac.toLowerCase())
                    && !clientId.toLowerCase().endsWith(successor.asset.mac.toLowerCase())) {
                evidence.add(new AssetLinkEvidence("DHCP_CLIENT_IDENTIFIER", 30, clientId));
                break;
            }
        }

        shared(evidence, predecessor, successor, AssetAttributeDimension.DHCP_FINGERPRINT, 15, attributeSpread);
        shared(evidence, predecessor, successor, AssetAttributeDimension.DHCP_PARAMETER_REQUEST_LIST, 10, attributeSpread);
        sharedFlat(evidence, predecessor, successor, AssetAttributeDimension.DHCP_VENDOR_CLASS, 5);

        Set<String> predecessorStacks = predecessor.values(AssetAttributeDimension.TCP_STACK);
        Set<String> successorStacks = successor.values(AssetAttributeDimension.TCP_STACK);
        if (!predecessorStacks.isEmpty() && !successorStacks.isEmpty()) {
            Set<String> sharedStacks = Sets.intersection(predecessorStacks, successorStacks);
            if (sharedStacks.isEmpty()) {
                evidence.add(new AssetLinkEvidence("TCP_STACK_MISMATCH", -20, null));
            } else {
                int weight = 0;
                for (String stack : sharedStacks) {
                    weight += (int) Math.round(10 * specificity(attributeSpread.getOrDefault(
                            AssetAttributeDimension.TCP_STACK.name() + "|" + stack, 2)));
                }
                evidence.add(new AssetLinkEvidence("TCP_STACK", Math.min(10, weight), sample(sharedStacks)));
            }
        }

        sharedFlat(evidence, predecessor, successor, AssetAttributeDimension.TCP_SERVER_FINGERPRINT, 10);

        Set<String> predecessorJa4 = predecessor.values(AssetAttributeDimension.JA4);
        Set<String> successorJa4 = successor.values(AssetAttributeDimension.JA4);
        if (!predecessorJa4.isEmpty() && !successorJa4.isEmpty()) {
            double jaccard = (double) Sets.intersection(predecessorJa4, successorJa4).size()
                    / Sets.union(predecessorJa4, successorJa4).size();
            int weight = (int) Math.round(20 * jaccard);
            if (weight > 0) {
                evidence.add(new AssetLinkEvidence("JA4", weight, Math.round(jaccard * 100) + "% overlap"));
            }
        }

        Set<String> predecessorPorts = predecessor.values(AssetAttributeDimension.INBOUND_PORT);
        Set<String> successorPorts = successor.values(AssetAttributeDimension.INBOUND_PORT);
        if (!predecessorPorts.isEmpty() && !successorPorts.isEmpty()) {
            if (predecessorPorts.equals(successorPorts)) {
                evidence.add(new AssetLinkEvidence("INBOUND_PORTS", 15, sample(successorPorts)));
            } else if (Sets.intersection(predecessorPorts, successorPorts).isEmpty()) {
                evidence.add(new AssetLinkEvidence("INBOUND_PORTS_MISMATCH", -10, null));
            }
        }

        sharedFlat(evidence, predecessor, successor, AssetAttributeDimension.NTP_CLIENT_VERSION, 5);

        Set<String> predecessorRanges = predecessor.values(AssetAttributeDimension.SOURCE_PORT_RANGE);
        Set<String> successorRanges = successor.values(AssetAttributeDimension.SOURCE_PORT_RANGE);
        if (!predecessorRanges.isEmpty() && !successorRanges.isEmpty()) {
            if (Sets.intersection(predecessorRanges, successorRanges).isEmpty()) {
                evidence.add(new AssetLinkEvidence("SOURCE_PORT_RANGE_MISMATCH", -10, null));
            } else {
                evidence.add(new AssetLinkEvidence("SOURCE_PORT_RANGE", 3, null));
            }
        }

        sharedFlat(evidence, predecessor, successor, AssetAttributeDimension.TCP_TIMESTAMP_RATE, 3);

        if (oui(predecessor.asset.mac).equals(oui(successor.asset.mac))) {
            evidence.add(new AssetLinkEvidence("VENDOR_PREFIX", 5, oui(successor.asset.mac)));
        }
        if (Tools.macAddressIsRandomized(successor.asset.mac)) {
            evidence.add(new AssetLinkEvidence("RANDOMIZED_MAC", 5, successor.asset.mac));
        }

        return evidence;
    }

    private static void shared(List<AssetLinkEvidence> evidence, Profile predecessor, Profile successor,
                               AssetAttributeDimension dimension, int weight, Map<String, Integer> spread) {
        Set<String> sharedValues = Sets.intersection(predecessor.values(dimension), successor.values(dimension));
        if (sharedValues.isEmpty()) {
            return;
        }

        int total = 0;
        for (String value : sharedValues) {
            total += (int) Math.round(weight * specificity(spread.getOrDefault(dimension.name() + "|" + value, 2)));
        }

        evidence.add(new AssetLinkEvidence(dimension.name(), Math.min(weight, total), sample(sharedValues)));
    }

    private static void sharedFlat(List<AssetLinkEvidence> evidence, Profile predecessor, Profile successor,
                                   AssetAttributeDimension dimension, int weight) {
        Set<String> sharedValues = Sets.intersection(predecessor.values(dimension), successor.values(dimension));
        if (!sharedValues.isEmpty()) {
            evidence.add(new AssetLinkEvidence(dimension.name(), weight, sample(sharedValues)));
        }
    }

    private static String sample(Collection<String> values) {
        return values.stream().sorted().limit(MAXIMUM_EVIDENCE_VALUES).collect(Collectors.joining(", "));
    }

    private static double specificity(int assetsSharingValue) {
        if (assetsSharingValue <= 2) {
            return 1.0;
        }
        if (assetsSharingValue <= 5) {
            return 0.5;
        }
        if (assetsSharingValue <= 20) {
            return 0.25;
        }
        return 0.1;
    }

    private static String oui(String mac) {
        return mac.length() >= 8 ? mac.substring(0, 8).toUpperCase() : mac.toUpperCase();
    }

    private static boolean isUsableAddress(String address) {
        return address != null && !address.equals("0.0.0.0") && !address.startsWith("169.254.")
                && !address.startsWith("fe80:") && !address.equals("::");
    }

    private static boolean linkExists(Handle handle, UUID organizationId, UUID tenantId, String predecessorMac,
                                      String successorMac) {
        return handle.createQuery("SELECT COUNT(*) FROM assets_links WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND predecessor_mac = :predecessor_mac " +
                        "AND successor_mac = :successor_mac")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("predecessor_mac", predecessorMac)
                .bind("successor_mac", successorMac)
                .mapTo(Long.class)
                .one() > 0;
    }

    private void raiseNewLinkAlert(AssetLinkEntry link, UUID organizationId, UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("link_uuid", link.uuid().toString());
        attributes.put("mac", link.successorMac());
        attributes.put("predecessor_mac", link.predecessorMac());
        attributes.put("confidence", String.valueOf(link.confidence()));
        attributes.put("signals", link.evidence().stream().map(AssetLinkEvidence::signal)
                .collect(Collectors.joining(", ")));

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                null,
                null,
                DetectionType.ASSETS_MAC_CHANGED,
                Subsystem.ETHERNET,
                "Asset \"" + link.predecessorMac() + "\" appears to continue as \"" + link.successorMac() +
                        "\" (confidence " + link.confidence() + ").",
                attributes,
                Set.of("link_uuid")
        );
    }

}
