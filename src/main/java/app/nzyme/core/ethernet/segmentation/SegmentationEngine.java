package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.profile.AssetProfileBatch;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.L4ConnectionCleaner;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelJson;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TenantCacheKey;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Every read of l4_sessions is bounded by a window. Everything else, including every view and every policy preview,
// reads the rollups.
public class SegmentationEngine {

    private static final Logger LOG = LogManager.getLogger(SegmentationEngine.class);

    private static final int CHUNK_MINUTES = 5;

    // Stored with every flow that has an external side.
    private static final int ASN_DETAIL_MAX_LENGTH = 255;
    private static final int RUN_BUDGET_SECONDS = 40;

    public static final int MAX_GROUPS_PER_CHUNK = 1_000_000;

    private static final int ICMP_MARGIN_SECONDS = 120;
    private static final int MAX_UNREACHABLES_PER_CHUNK = 250_000;

    public static final int MAX_PORTS_PER_PAIR = 32;
    public static final int MAX_ATTEMPT_ROWS_PER_SOURCE = 256;

    public static final int MAX_FINDINGS_PER_POLICY_PER_CHUNK = 1000;

    private static final String EXCLUSIONS = "AND NOT source_address_is_loopback " +
            "AND NOT destination_address_is_loopback AND NOT source_address_is_multicast ";

    // A frame to the broadcast address of the link is how the broadcast address of a subnet is told apart from a host
    // without knowing the subnet.
    private static final String BROADCAST = "(destination_address_is_multicast " +
            "OR destination_address = '255.255.255.255'::inet " +
            "OR COALESCE(destination_mac = '" + AssetProfileBatch.BROADCAST_MAC + "', false))";

    /*
     * Judged by what came back and not by the state: older taps turn a retransmitted SYN into an established session
     * and a retry after a reset into a reset session. At most three small segments back and no SYN-ACK is a
     * rejection. Anything more is taken as connected with a SYN-ACK the tap missed.
     */
    private static final String OUTCOME = "CASE WHEN l4_type = 'TCP' THEN " +
            "CASE WHEN tcp_synack_ip_ttl IS NOT NULL THEN 'CONNECTED' " +
            "WHEN COALESCE(bytes_rx_count, 0) = 0 THEN 'NO_REPLY' " +
            "WHEN state = 'REFUSED' THEN 'REJECTED' " +
            "WHEN segments_count_client_to_server IS NOT NULL " +
            "AND segments_count - segments_count_client_to_server <= 3 AND bytes_rx_count <= 250 THEN 'REJECTED' " +
            "ELSE 'CONNECTED' END " +
            "ELSE CASE WHEN COALESCE(bytes_rx_count, 0) > 0 THEN 'CONNECTED' ELSE 'NO_REPLY' END END";

    // A UDP conversation that is a reply whose request the tap did not see is turned around, so that it counts for the
    // service that was asked and not as a connection from the server to a random port of the client.
    private static final String ORIENTED = "SELECT tap_uuid, start_time, most_recent_segment_time, l4_type, tags, " +
            "CASE WHEN reversed THEN destination_address ELSE source_address END AS src, " +
            "CASE WHEN reversed THEN source_address ELSE destination_address END AS dst, " +
            "CASE WHEN reversed THEN destination_address_is_site_local ELSE source_address_is_site_local END AS src_local, " +
            "CASE WHEN reversed THEN source_address_is_site_local ELSE destination_address_is_site_local END AS dst_local, " +
            "CASE WHEN reversed THEN destination_address_geo_asn_number ELSE source_address_geo_asn_number END AS src_asn, " +
            "CASE WHEN reversed THEN source_address_geo_asn_number ELSE destination_address_geo_asn_number END AS dst_asn, " +
            "CASE WHEN reversed THEN destination_address_geo_asn_name ELSE source_address_geo_asn_name END AS src_asn_name, " +
            "CASE WHEN reversed THEN source_address_geo_asn_name ELSE destination_address_geo_asn_name END AS dst_asn_name, " +
            "CASE WHEN reversed THEN destination_address_geo_asn_domain ELSE source_address_geo_asn_domain END AS src_asn_domain, " +
            "CASE WHEN reversed THEN source_address_geo_asn_domain ELSE destination_address_geo_asn_domain END AS dst_asn_domain, " +
            "CASE WHEN reversed THEN destination_address_geo_country_code ELSE source_address_geo_country_code END AS src_cc, " +
            "CASE WHEN reversed THEN source_address_geo_country_code ELSE destination_address_geo_country_code END AS dst_cc, " +
            "CASE WHEN reversed THEN source_port ELSE destination_port END AS port, " +
            "CASE WHEN broadcast THEN 'SENT' WHEN reversed THEN 'CONNECTED' ELSE " + OUTCOME + " END AS outcome, " +
            "broadcast " +
            "FROM (SELECT *, " + BROADCAST + " AS broadcast, (NOT " + BROADCAST + " AND l4_type = 'UDP' " +
            "AND ((source_port < 1024 AND destination_port >= 1024) " +
            "OR (source_port = 67 AND destination_port = 68) " +
            "OR (source_port < 32768 AND destination_port >= 32768 AND COALESCE(bytes_rx_count, 0) = 0))) AS reversed " +
            "FROM l4_sessions WHERE ";

    private static final String GROUPED_COLUMNS = "tap_uuid, l4_type, host(src) AS src, host(dst) AS dst, " +
            "COALESCE(src_local, false) AS src_local, COALESCE(dst_local, false) AS dst_local, src_asn, src_cc, " +
            "dst_asn, dst_cc, port, tags::text AS tags, outcome, broadcast, COUNT(*) AS sessions, " +
            "MAX(most_recent_segment_time) AS last_seen, " +
            "LEFT(MAX(src_asn_name), " + ASN_DETAIL_MAX_LENGTH + ") AS src_asn_name, " +
            "LEFT(MAX(src_asn_domain), " + ASN_DETAIL_MAX_LENGTH + ") AS src_asn_domain, " +
            "LEFT(MAX(dst_asn_name), " + ASN_DETAIL_MAX_LENGTH + ") AS dst_asn_name, " +
            "LEFT(MAX(dst_asn_domain), " + ASN_DETAIL_MAX_LENGTH + ") AS dst_asn_domain";

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    private final Timer runTimer;
    private final Timer chunkTimer;
    private final Timer restampTimer;
    private final Timer backfillTimer;
    private final Meter sessionsMeter;
    private final Meter flowsMeter;
    private final Meter findingsMeter;
    private final Meter alertsMeter;

    private final Map<TenantCacheKey, Long> watermarks = new ConcurrentHashMap<>();

    public SegmentationEngine(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.segmentation = nzyme.getEthernet().segmentation();

        this.runTimer = nzyme.getMetrics().timer(MetricNames.SEGMENTATION_RUN_TIMER);
        this.chunkTimer = nzyme.getMetrics().timer(MetricNames.SEGMENTATION_CHUNK_TIMER);
        this.restampTimer = nzyme.getMetrics().timer(MetricNames.SEGMENTATION_RESTAMP_TIMER);
        this.backfillTimer = nzyme.getMetrics().timer(MetricNames.SEGMENTATION_BACKFILL_TIMER);
        this.sessionsMeter = nzyme.getMetrics().meter(MetricNames.SEGMENTATION_SESSIONS_METER);
        this.flowsMeter = nzyme.getMetrics().meter(MetricNames.SEGMENTATION_FLOWS_METER);
        this.findingsMeter = nzyme.getMetrics().meter(MetricNames.SEGMENTATION_FINDINGS_METER);
        this.alertsMeter = nzyme.getMetrics().meter(MetricNames.SEGMENTATION_ALERTS_METER);

        if (!nzyme.getMetrics().getGauges().containsKey(MetricNames.SEGMENTATION_LAG_SECONDS)) {
            nzyme.getMetrics().gauge(MetricNames.SEGMENTATION_LAG_SECONDS, () -> this::maximumLagSeconds);
        }
    }

    public long maximumLagSeconds() {
        long now = DateTime.now().getMillis();
        return watermarks.values().stream().mapToLong(watermark -> Math.max(0, (now - watermark) / 1000)).max().orElse(0);
    }

    private record Group(UUID tap,
                         DateTime bucket,
                         String l4Type,
                         String source,
                         String destination,
                         boolean sourceLocal,
                         boolean destinationLocal,
                         @Nullable Long sourceAsn,
                         @Nullable String sourceAsnName,
                         @Nullable String sourceAsnDomain,
                         @Nullable String sourceCountryCode,
                         @Nullable Long destinationAsn,
                         @Nullable String destinationAsnName,
                         @Nullable String destinationAsnDomain,
                         @Nullable String destinationCountryCode,
                         int port,
                         @Nullable String tags,
                         Outcome outcome,
                         boolean broadcast,
                         long sessions,
                         DateTime firstSeen,
                         DateTime lastSeen) { }

    private record Alert(Policy policy,
                         FindingClass findingClass,
                         long sources,
                         long sessions,
                         Findings.Violation example,
                         boolean broadcastOnly) { }

    private record Result(boolean progressed, long sessions, long flows, long findings, List<Alert> alerts) {

        static Result none() {
            return new Result(false, 0, 0, 0, Collections.emptyList());
        }

    }

    private static class Definitions {
        final MembershipResolver resolver;
        final List<PolicyMatcher> matchers;
        final String policiesVersion;
        final Map<Long, List<Exclusion>> exclusions;
        final String exclusionsVersion;

        Definitions(MembershipResolver resolver,
                    List<PolicyMatcher> matchers,
                    String policiesVersion,
                    Map<Long, List<Exclusion>> exclusions,
                    String exclusionsVersion) {
            this.resolver = resolver;
            this.matchers = matchers;
            this.policiesVersion = policiesVersion;
            this.exclusions = exclusions;
            this.exclusionsVersion = exclusionsVersion;
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        SegmentationConfiguration config = segmentation.getConfiguration(organizationId, tenantId);
        if (!config.enabled()) {
            LOG.debug("Segmentation is disabled for tenant [{}/{}]. Skipping.", organizationId, tenantId);
            watermarks.remove(TenantCacheKey.create(organizationId, tenantId));
            return;
        }

        List<UUID> taps = Tools.getTapUuids(nzyme, organizationId, tenantId);
        if (taps.isEmpty()) {
            LOG.debug("Tenant [{}/{}] has no taps. Skipping segmentation.", organizationId, tenantId);
            watermarks.remove(TenantCacheKey.create(organizationId, tenantId));
            return;
        }

        DateTime started = DateTime.now();
        DateTime deadline = started.plusSeconds(RUN_BUDGET_SECONDS);

        // Fixed for the run. Following the clock, every chunk would find a few more milliseconds to read and the
        // run would never end before its budget.
        DateTime until = started.minusSeconds(config.settleSeconds());
        long sessions = 0;
        long flows = 0;
        long findings = 0;
        boolean ran = false;

        try (Timer.Context ignored = runTimer.time()) {
            restamp(organizationId, tenantId);

            Definitions definitions = nzyme.getDatabase().withHandle(handle -> definitions(handle, organizationId, tenantId));

            while (true) {
                Result result;
                try (Timer.Context ignoredChunk = chunkTimer.time()) {
                    result = nzyme.getDatabase().inTransaction(handle ->
                            processChunk(handle, organizationId, tenantId, taps, config, until, definitions));
                }

                raise(result.alerts(), organizationId, tenantId);

                if (!result.progressed()) {
                    break;
                }

                ran = true;
                sessions += result.sessions();
                flows += result.flows();
                findings += result.findings();

                if (DateTime.now().isAfter(deadline)) {
                    LOG.info("Segmentation of tenant [{}/{}] is behind and continues in the next run.",
                            organizationId, tenantId);
                    break;
                }
            }

            Result carried = nzyme.getDatabase().inTransaction(handle ->
                    carryOver(handle, organizationId, tenantId, taps, definitions));
            raise(carried.alerts(), organizationId, tenantId);
            flows += carried.flows();
            findings += carried.findings();
        }

        long duration = DateTime.now().getMillis() - started.getMillis();
        long finalSessions = sessions;
        long finalFlows = flows;
        long finalFindings = findings;
        boolean finalRan = ran;

        nzyme.getDatabase().useHandle(handle -> {
            EngineState state = segmentation.state(handle, organizationId, tenantId, false);
            if (state.watermark() != null) {
                watermarks.put(TenantCacheKey.create(organizationId, tenantId), state.watermark().getMillis());
            }

            if (!finalRan) {
                return;
            }

            handle.createUpdate("UPDATE segmentation_engine_state SET last_run_at = NOW(), " +
                            "last_run_duration_ms = :duration, last_run_sessions = :sessions, " +
                            "last_run_flows = :flows, last_run_findings = :findings, updated_at = NOW() " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                    .bind("duration", duration)
                    .bind("sessions", finalSessions)
                    .bind("flows", finalFlows)
                    .bind("findings", finalFindings)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .execute();
        });
    }

    private Definitions definitions(Handle handle, UUID organizationId, UUID tenantId) {
        // Read before the policies and exclusions, so that a change in between is noticed and not missed.
        String policiesVersion = policiesVersion(handle, organizationId, tenantId);
        String exclusionsVersion = Exclusions.version(handle, organizationId, tenantId);
        List<Policy> policies = segmentation.policies().findAll(handle, organizationId, tenantId);
        MembershipResolver resolver = segmentation.buildResolver(handle, organizationId, tenantId, policies, null);

        List<PolicyMatcher> matchers = Lists.newArrayList();
        for (Policy policy : policies) {
            if (policy.status() == PolicyStatus.PAUSED) {
                continue;
            }

            try {
                matchers.add(new PolicyMatcher(policy));
            } catch (Exception e) {
                LOG.error("Could not prepare segmentation policy [{}]. Skipping it.", policy.uuid(), e);
            }
        }

        return new Definitions(resolver, matchers, policiesVersion,
                segmentation.exclusions().findAllPerSegment(handle, organizationId, tenantId), exclusionsVersion);
    }

    private static String policiesVersion(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT COUNT(*) || ':' || COALESCE(MAX(updated_at)::text, '') " +
                        "FROM segmentation_policies WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .mapTo(String.class)
                .one();
    }

    private static boolean lock(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                .bind("key", Segmentation.lockKey(organizationId, tenantId))
                .mapTo(Boolean.class)
                .one();
    }

    private Result processChunk(Handle handle,
                                UUID organizationId,
                                UUID tenantId,
                                List<UUID> taps,
                                SegmentationConfiguration config,
                                DateTime until,
                                Definitions definitions) {
        if (!lock(handle, organizationId, tenantId)) {
            return Result.none();
        }

        EngineState state = segmentation.state(handle, organizationId, tenantId, true);

        // Segments, policies or exclusions changed since this run loaded them, here or on another node. The run ends,
        // and the next one applies the change before it reads on.
        if (state.restampPending()
                || !definitions.policiesVersion.equals(policiesVersion(handle, organizationId, tenantId))
                || !definitions.exclusionsVersion.equals(Exclusions.version(handle, organizationId, tenantId))) {
            return Result.none();
        }

        DateTime from = state.watermark() == null ? until.minusHours(config.initialBackfillHours()) : state.watermark();
        if (!from.isBefore(until)) {
            if (state.watermark() == null) {
                // Nothing to read on a first run without backfill. The next run continues from here.
                setWatermark(handle, organizationId, tenantId, until);
            }

            return Result.none();
        }

        DateTime to = from.plusMinutes(CHUNK_MINUTES);
        if (to.isAfter(until)) {
            to = until;
        }

        List<Group> groups = readGroups(handle, organizationId, tenantId, taps, from, to);
        Set<String> unreachable = groups.isEmpty() ? Collections.emptySet() : readUnreachables(handle, taps, from, to);

        Result result = evaluate(handle, organizationId, tenantId, groups, unreachable, definitions,
                definitions.matchers, Collections.emptyMap(), false, false);

        setWatermark(handle, organizationId, tenantId, to);

        return new Result(true, result.sessions(), result.flows(), result.findings(), result.alerts());
    }

    private List<Group> readGroups(Handle handle,
                                   UUID organizationId,
                                   UUID tenantId,
                                   List<UUID> taps,
                                   DateTime from,
                                   DateTime to) {
        List<Group> groups = handle.createQuery("SELECT date_trunc('hour', start_time, 'UTC') AS bucket, " +
                        "MIN(start_time) AS first_seen, " + GROUPED_COLUMNS + " FROM (" + ORIENTED +
                        "tap_uuid IN (<taps>) AND created_at >= :from AND created_at < :to " + EXCLUSIONS +
                        ") AS s) AS o GROUP BY bucket, tap_uuid, l4_type, src, dst, src_local, dst_local, src_asn, " +
                        "src_cc, dst_asn, dst_cc, port, tags, outcome, broadcast LIMIT :limit")
                .bindList("taps", taps)
                .bind("from", from)
                .bind("to", to)
                .bind("limit", MAX_GROUPS_PER_CHUNK)
                .map((rs, ctx) -> group(rs, new DateTime(rs.getTimestamp("bucket")), new DateTime(rs.getTimestamp("first_seen"))))
                .list();

        if (groups.size() >= MAX_GROUPS_PER_CHUNK) {
            LOG.warn("Segmentation window [{} - {}] of tenant [{}/{}] has more than [{}] distinct flows. " +
                    "The rest is not evaluated.", from, to, organizationId, tenantId, MAX_GROUPS_PER_CHUNK);
            markLimitReached(handle, organizationId, tenantId);
        }

        return groups;
    }

    // Sessions that were evaluated before a policy was created or forbade more are judged again against it. Ends when
    // nothing is left, when another evaluation of the tenant is running, when definitions changed or at the deadline.
    public void backfill(UUID organizationId, UUID tenantId, DateTime deadline) {
        SegmentationConfiguration config = segmentation.getConfiguration(organizationId, tenantId);
        List<UUID> taps = Tools.getTapUuids(nzyme, organizationId, tenantId);
        if (!config.enabled() || taps.isEmpty()) {
            nzyme.getDatabase().useHandle(handle ->
                    segmentation.policies().cancelBackfills(handle, organizationId, tenantId));
            return;
        }

        Definitions definitions = nzyme.getDatabase().withHandle(handle -> definitions(handle, organizationId, tenantId));

        while (DateTime.now().isBefore(deadline)) {
            boolean progressed;
            try (Timer.Context ignored = backfillTimer.time()) {
                progressed = nzyme.getDatabase().inTransaction(handle ->
                        backfillChunk(handle, organizationId, tenantId, taps, definitions));
            }

            if (!progressed) {
                return;
            }
        }
    }

    private boolean backfillChunk(Handle handle,
                                  UUID organizationId,
                                  UUID tenantId,
                                  List<UUID> taps,
                                  Definitions definitions) {
        if (!lock(handle, organizationId, tenantId)) {
            return false;
        }

        EngineState state = segmentation.state(handle, organizationId, tenantId, true);

        // Before its first run the engine has evaluated nothing that could be judged again.
        if (state.watermark() == null || state.restampPending()
                || !definitions.policiesVersion.equals(policiesVersion(handle, organizationId, tenantId))
                || !definitions.exclusionsVersion.equals(Exclusions.version(handle, organizationId, tenantId))) {
            return false;
        }

        List<Policy> pending = segmentation.policies().findAllWithPendingBackfill(handle, organizationId, tenantId);
        if (pending.isEmpty()) {
            return false;
        }

        List<Policy> unplaced = pending.stream()
                .filter(p -> p.backfillCursor() == null || p.backfillFrom() == null)
                .collect(Collectors.toList());
        if (!unplaced.isEmpty()) {
            // Nothing is judged that has no flows to show for, and nothing is read from before the oldest session.
            DateTime evaluated = segmentation.flows().earliestBucket(handle, organizationId, tenantId);
            DateTime stored = oldestSession(handle, taps);
            DateTime from = evaluated == null || stored == null ? null : (stored.isAfter(evaluated) ? stored : evaluated);

            for (Policy policy : unplaced) {
                boolean unknown = policy.backfillCursor() == null && segmentation.findings().exist(handle, policy);

                if (from == null || unknown) {
                    segmentation.policies().completeBackfill(handle, List.of(policy));
                    continue;
                }

                // A policy that forbids nothing more than when it was paused has nothing to add from before the pause.
                DateTime policyFrom = from;
                if (policy.backfillPreviousScope() != null && policy.backfillPreviousUntil() != null
                        && policy.backfillPreviousUntil().isAfter(from)
                        && !Policies.forbidsMore(policy.scope(), policy.allowed(), policy.backfillPreviousScope(),
                        policy.backfillPreviousAllowed())) {
                    policyFrom = policy.backfillPreviousUntil();
                }

                segmentation.policies().setBackfill(handle, List.of(policy),
                        policy.backfillCursor() == null ? state.watermark() : policy.backfillCursor(), policyFrom);
            }

            return true;
        }

        // Policies that were requested together share their windows and are judged in one read.
        Policy first = pending.get(0);
        List<Policy> batch = pending.stream()
                .filter(p -> p.backfillCursor().isEqual(first.backfillCursor()) && p.backfillFrom().isEqual(first.backfillFrom()))
                .collect(Collectors.toList());

        DateTime to = first.backfillCursor();
        DateTime from = to.minusMinutes(CHUNK_MINUTES);
        if (from.isBefore(first.backfillFrom())) {
            from = first.backfillFrom();
        }

        // A window never reaches across the time up to which the previous definition of one of its policies applies.
        for (Policy policy : batch) {
            DateTime until = policy.backfillPreviousUntil();
            if (until != null && until.isAfter(from) && until.isBefore(to)) {
                from = until;
            }
        }

        if (from.isBefore(to)) {
            List<PolicyMatcher> matchers = Lists.newArrayList();
            Map<Long, PolicyMatcher> previous = Maps.newHashMap();
            for (Policy policy : batch) {
                try {
                    boolean evaluatedBefore = policy.backfillPreviousScope() != null
                            && (policy.backfillPreviousUntil() == null || !to.isAfter(policy.backfillPreviousUntil()));
                    PolicyMatcher before = !evaluatedBefore ? null
                            : new PolicyMatcher(null, policy.backfillPreviousScope(), policy.backfillPreviousAllowed());

                    matchers.add(new PolicyMatcher(policy));
                    if (before != null) {
                        previous.put(policy.id(), before);
                    }
                } catch (Exception e) {
                    LOG.error("Could not prepare segmentation policy [{}]. Skipping it.", policy.uuid(), e);
                }
            }

            List<Group> groups = readGroups(handle, organizationId, tenantId, taps, from, to);
            Set<String> unreachable = groups.isEmpty() ? Collections.emptySet() : readUnreachables(handle, taps, from, to);
            evaluate(handle, organizationId, tenantId, groups, unreachable, definitions, matchers, previous, false, true);
        }

        if (from.isAfter(first.backfillFrom())) {
            segmentation.policies().setBackfill(handle, batch, from, first.backfillFrom());
        } else {
            segmentation.policies().completeBackfill(handle, batch);
        }

        return true;
    }

    // Per tap, so that the first entry of the index answers it.
    @Nullable
    private static DateTime oldestSession(Handle handle, List<UUID> taps) {
        return handle.createQuery("SELECT MIN(oldest) AS oldest FROM unnest(ARRAY[<taps>]::uuid[]) AS t(tap) " +
                        "CROSS JOIN LATERAL (SELECT MIN(created_at) AS oldest FROM l4_sessions " +
                        "WHERE tap_uuid = t.tap) AS s")
                .bindList("taps", taps)
                .map((rs, ctx) -> Optional.ofNullable(rs.getTimestamp("oldest")).map(DateTime::new))
                .one()
                .orElse(null);
    }

    private static void markLimitReached(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createUpdate("UPDATE segmentation_engine_state SET limit_reached_at = NOW() " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    private static void setWatermark(Handle handle, UUID organizationId, UUID tenantId, DateTime watermark) {
        handle.createUpdate("UPDATE segmentation_engine_state SET watermark = :watermark, updated_at = NOW() " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                .bind("watermark", watermark)
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    // After a break only the last full hour is carried, because all that is known about a session is whether it is
    // open now.
    private Result carryOver(Handle handle,
                             UUID organizationId,
                             UUID tenantId,
                             List<UUID> taps,
                             Definitions definitions) {
        if (!lock(handle, organizationId, tenantId)) {
            return Result.none();
        }

        EngineState state = segmentation.state(handle, organizationId, tenantId, true);

        // The next run carries over with the exclusions as they are now.
        if (!definitions.exclusionsVersion.equals(Exclusions.version(handle, organizationId, tenantId))) {
            return Result.none();
        }

        DateTime bucket = Flows.bucketOf(DateTime.now()).minusHours(1);

        Result result = Result.none();
        if (state.carryoverBucket() != null && state.carryoverBucket().isBefore(bucket)) {
            List<Group> groups = handle.createQuery("SELECT " + GROUPED_COLUMNS + " FROM (" + ORIENTED +
                            "tap_uuid IN (<taps>) AND l4_type IN ('TCP', 'UDP') " +
                            "AND most_recent_segment_time >= :bucket AND " + L4ConnectionCleaner.OPEN_STATE + " " +
                            "AND created_at < :bucket " + EXCLUSIONS + ") AS s) AS o " +
                            "GROUP BY tap_uuid, l4_type, src, dst, src_local, dst_local, src_asn, src_cc, dst_asn, " +
                            "dst_cc, port, tags, outcome, broadcast LIMIT :limit")
                    .bindList("taps", taps)
                    .bind("bucket", bucket)
                    .bind("limit", MAX_GROUPS_PER_CHUNK)
                    .map((rs, ctx) -> group(rs, bucket, bucket))
                    .list();

            if (groups.size() >= MAX_GROUPS_PER_CHUNK) {
                markLimitReached(handle, organizationId, tenantId);
            }

            result = evaluate(handle, organizationId, tenantId, groups, Collections.emptySet(), definitions,
                    definitions.matchers, Collections.emptyMap(), true, false);
        }

        if (state.carryoverBucket() == null || state.carryoverBucket().isBefore(bucket)) {
            handle.createUpdate("UPDATE segmentation_engine_state SET carryover_bucket = :bucket, updated_at = NOW() " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                    .bind("bucket", bucket)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .execute();
        }

        return result;
    }

    private static Group group(java.sql.ResultSet rs, DateTime bucket, DateTime firstSeen) throws java.sql.SQLException {
        long sourceAsn = rs.getLong("src_asn");
        Long sourceAsnOrNull = rs.wasNull() ? null : sourceAsn;
        long destinationAsn = rs.getLong("dst_asn");
        Long destinationAsnOrNull = rs.wasNull() ? null : destinationAsn;

        return new Group(
                UUID.fromString(rs.getString("tap_uuid")),
                bucket,
                rs.getString("l4_type"),
                rs.getString("src"),
                rs.getString("dst"),
                rs.getBoolean("src_local"),
                rs.getBoolean("dst_local"),
                sourceAsnOrNull,
                rs.getString("src_asn_name"),
                rs.getString("src_asn_domain"),
                rs.getString("src_cc"),
                destinationAsnOrNull,
                rs.getString("dst_asn_name"),
                rs.getString("dst_asn_domain"),
                rs.getString("dst_cc"),
                rs.getInt("port"),
                rs.getString("tags"),
                Outcome.valueOf(rs.getString("outcome")),
                rs.getBoolean("broadcast"),
                rs.getLong("sessions"),
                firstSeen,
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

    private Set<String> readUnreachables(Handle handle, List<UUID> taps, DateTime from, DateTime to) {
        return Sets.newHashSet(handle.createQuery("SELECT DISTINCT host(quoted_source_address) AS src, " +
                        "host(quoted_destination_address) AS dst, quoted_protocol, quoted_destination_port " +
                        "FROM icmp_packets WHERE tap_uuid IN (<taps>) AND last_seen >= :from AND last_seen <= :to " +
                        "AND icmp_type = 3 AND quoted_protocol IN (6, 17) AND quoted_source_address IS NOT NULL " +
                        "AND quoted_destination_address IS NOT NULL AND quoted_destination_port IS NOT NULL " +
                        "LIMIT :limit")
                .bindList("taps", taps)
                .bind("from", from.minusSeconds(ICMP_MARGIN_SECONDS))
                .bind("to", to.plusSeconds(ICMP_MARGIN_SECONDS))
                .bind("limit", MAX_UNREACHABLES_PER_CHUNK)
                .map((rs, ctx) -> unreachableKey(rs.getString("src"), rs.getString("dst"),
                        rs.getInt("quoted_protocol") == 6 ? "TCP" : "UDP", rs.getInt("quoted_destination_port")))
                .list());
    }

    private static String unreachableKey(String source, String destination, String l4Type, int port) {
        return source + "|" + destination + "|" + l4Type + "|" + port;
    }

    // Sessions that are judged again were written to the rollup and counted for the exclusions when they were
    // evaluated first, and raise no alerts. What the previous definition of a policy forbade was recorded then.
    private Result evaluate(Handle handle,
                            UUID organizationId,
                            UUID tenantId,
                            List<Group> groups,
                            Set<String> unreachable,
                            Definitions definitions,
                            List<PolicyMatcher> matchers,
                            Map<Long, PolicyMatcher> previous,
                            boolean continued,
                            boolean again) {
        if (groups.isEmpty()) {
            return Result.none();
        }

        Map<String, InetAddress> addresses = Maps.newHashMap();
        List<Flow> flows = Lists.newArrayListWithCapacity(groups.size());
        ViolationCollector violations = new ViolationCollector();
        Map<Long, Map<UUID, Long>> excluded = Maps.newHashMap();
        long sessions = 0;

        for (Group group : groups) {
            InetAddress sourceAddress;
            InetAddress destinationAddress;
            try {
                sourceAddress = addresses.computeIfAbsent(group.source(), InetAddresses::forString);
                destinationAddress = addresses.computeIfAbsent(group.destination(), InetAddresses::forString);
            } catch (Exception e) {
                continue;
            }

            if (sourceAddress.equals(destinationAddress)) {
                continue;
            }

            Endpoint source = definitions.resolver.resolve(group.tap(), sourceAddress, group.sourceLocal(),
                    group.sourceAsn(), group.sourceCountryCode())
                    .withAsnDetails(group.sourceAsnName(), group.sourceAsnDomain());
            Endpoint destination = group.broadcast()
                    ? Endpoint.broadcast(destinationAddress)
                    : definitions.resolver.resolve(group.tap(), destinationAddress, group.destinationLocal(),
                            group.destinationAsn(), group.destinationCountryCode())
                    .withAsnDetails(group.destinationAsnName(), group.destinationAsnDomain());

            if (!source.internal() && !destination.internal()) {
                continue;
            }

            Outcome outcome = group.outcome();
            if (outcome == Outcome.NO_REPLY && unreachable.contains(
                    unreachableKey(group.source(), group.destination(), group.l4Type(), group.port()))) {
                outcome = Outcome.REJECTED;
            }

            if (outcome.findingClass() == FindingClass.ATTEMPTED) {
                Exclusion exclusion = Exclusions.excluding(definitions.exclusions, source, destination);
                if (exclusion != null) {
                    excluded.computeIfAbsent(exclusion.id(), k -> Maps.newHashMap())
                            .merge(group.tap(), group.sessions(), Long::sum);
                    continue;
                }
            }

            String protocol = AssetProfileBatch.protocolOf(DnsTunnelJson.parseStrings(group.tags()));
            sessions += group.sessions();

            for (PolicyMatcher matcher : matchers) {
                if (matcher.evaluate(source, destination, group.l4Type(), group.port(), protocol)
                        != PolicyMatcher.Verdict.VIOLATION) {
                    continue;
                }

                PolicyMatcher before = previous.get(matcher.policy().id());
                if (before == null || before.evaluate(source, destination, group.l4Type(), group.port(), protocol)
                        != PolicyMatcher.Verdict.VIOLATION) {
                    violations.add(matcher.policy(), source, destination, group, outcome, protocol, continued);
                }
            }

            if (again) {
                continue;
            }

            flows.add(new Flow(
                    group.tap(),
                    group.bucket(),
                    source,
                    destination,
                    Flows.key(source),
                    Flows.key(destination),
                    group.l4Type(),
                    group.port(),
                    protocol,
                    outcome,
                    continued ? 0 : group.sessions(),
                    continued ? group.sessions() : 0,
                    group.firstSeen(),
                    group.lastSeen()
            ));
        }

        List<Flow> folded = fold(flows);
        if (!again) {
            segmentation.flows().write(handle, organizationId, tenantId, folded);

            // Like in the crossings, the tap that saw the most counts where several taps recorded an attempt.
            Map<Long, Long> matches = Maps.newHashMap();
            excluded.forEach((exclusion, perTap) -> matches.put(exclusion, Collections.max(perTap.values())));
            segmentation.exclusions().recordMatches(handle, matches);
        }

        List<Findings.Violation> found = violations.violations(MAX_FINDINGS_PER_POLICY_PER_CHUNK);
        if (violations.dropped() > 0) {
            LOG.warn("Segmentation of tenant [{}/{}] found more sources per policy than are recorded per window. " +
                    "[{}] were left out.", organizationId, tenantId, violations.dropped());
            markLimitReached(handle, organizationId, tenantId);
        }

        int opened = segmentation.findings().record(handle, organizationId, tenantId, found);
        findingsMeter.mark(opened);

        if (again) {
            return new Result(true, sessions, 0, opened, Collections.emptyList());
        }

        sessionsMeter.mark(sessions);
        flowsMeter.mark(folded.size());

        return new Result(true, sessions, folded.size(), opened, alerts(found));
    }

    private static List<Alert> alerts(List<Findings.Violation> violations) {
        Map<String, Alert> alerts = Maps.newLinkedHashMap();
        for (Findings.Violation violation : violations) {
            if (!violation.policy().alertsOn(violation.findingClass())) {
                continue;
            }

            boolean broadcastOnly = violation.targets().stream().allMatch(t -> t.outcome() == Outcome.SENT);

            String key = violation.policy().id() + "|" + violation.findingClass();
            Alert existing = alerts.get(key);
            if (existing == null) {
                alerts.put(key, new Alert(violation.policy(), violation.findingClass(), 1, violation.sessions(),
                        violation, broadcastOnly));
            } else {
                alerts.put(key, new Alert(existing.policy(), existing.findingClass(), existing.sources() + 1,
                        existing.sessions() + violation.sessions(), existing.example(),
                        existing.broadcastOnly() && broadcastOnly));
            }
        }

        return Lists.newArrayList(alerts.values());
    }

    // An external source is a network. One of its addresses is shown, never the key it is stored under.
    static String exampleSource(Findings.Violation violation) {
        if (violation.sourceAddress() != null) {
            return violation.sourceAddress();
        }

        if (!violation.sourceAddresses().isEmpty()) {
            return violation.sourceAddresses().iterator().next();
        }

        if (violation.sourceAsn() != null) {
            return "AS" + violation.sourceAsn();
        }

        return violation.sourceCountryCode() != null ? violation.sourceCountryCode() : "Internet";
    }

    private void raise(List<Alert> alerts, UUID organizationId, UUID tenantId) {
        for (Alert alert : alerts) {
            try {
                boolean crossed = alert.findingClass() == FindingClass.CROSSED;
                Findings.Violation example = alert.example();

                Map<String, String> attributes = Maps.newHashMap();
                attributes.put("policy_uuid", alert.policy().uuid().toString());
                attributes.put("policy_name", alert.policy().name());
                attributes.put("finding_class", alert.findingClass().name());
                attributes.put("example_source", exampleSource(example));

                if (!example.targets().isEmpty()) {
                    Finding.Target target = example.targets().get(0);
                    attributes.put("example_destination", target.address());
                    attributes.put("example_service", target.l4Type() + "/" + target.port());
                    attributes.put("example_outcome", target.outcome().name());
                }

                String sources = alert.sources() == 1 ? "One source" : alert.sources() + " sources";
                String details;
                if (crossed && alert.broadcastOnly()) {
                    details = sources + " sent broadcast or multicast traffic across segmentation policy \""
                            + alert.policy().name() + "\".";
                } else if (crossed) {
                    details = sources + " connected across segmentation policy \"" + alert.policy().name() + "\".";
                } else {
                    details = sources + " attempted to connect across segmentation policy \""
                            + alert.policy().name() + "\".";
                }

                nzyme.getDetectionAlertService().raiseAlert(
                        organizationId,
                        tenantId,
                        null,
                        null,
                        crossed ? DetectionType.SEGMENTATION_POLICY_CROSSED : DetectionType.SEGMENTATION_POLICY_ATTEMPT,
                        Subsystem.ETHERNET,
                        details,
                        attributes,
                        Set.of("policy_uuid")
                );

                alertsMeter.mark();
            } catch (Exception e) {
                LOG.error("Could not raise segmentation alert for policy [{}].", alert.policy().uuid(), e);
            }
        }
    }

    // Keeps a sweep from filling the rollup. Connections that were answered are never folded: there are only as many
    // of them as there are services that answer.
    static List<Flow> fold(List<Flow> flows) {
        List<Flow> result = Lists.newArrayList();
        Map<String, List<Flow>> perPair = Maps.newLinkedHashMap();

        for (Flow flow : flows) {
            if (flow.outcome() == Outcome.CONNECTED) {
                result.add(flow);
                continue;
            }

            String pair = flow.bucket().getMillis() + "|" + flow.tap() + "|" + flow.sourceKey() + "|"
                    + flow.destinationKey() + "|" + flow.l4Type();
            perPair.computeIfAbsent(pair, k -> Lists.newArrayList()).add(flow);
        }

        Map<String, List<Flow>> perSource = Maps.newLinkedHashMap();
        for (List<Flow> pair : perPair.values()) {
            List<Flow> kept = pair;
            if (distinctPorts(pair) > MAX_PORTS_PER_PAIR) {
                kept = merge(pair, null);
            }

            for (Flow flow : kept) {
                String source = flow.bucket().getMillis() + "|" + flow.tap() + "|" + flow.sourceKey();
                perSource.computeIfAbsent(source, k -> Lists.newArrayList()).add(flow);
            }
        }

        for (List<Flow> source : perSource.values()) {
            if (source.size() <= MAX_ATTEMPT_ROWS_PER_SOURCE) {
                result.addAll(source);
                continue;
            }

            source.sort(Comparator.comparingLong((Flow f) -> f.sessions() + f.continued()).reversed());
            result.addAll(source.subList(0, MAX_ATTEMPT_ROWS_PER_SOURCE));

            // External destinations are per network already.
            Map<String, List<Flow>> perSegment = Maps.newLinkedHashMap();
            for (Flow flow : source.subList(MAX_ATTEMPT_ROWS_PER_SOURCE, source.size())) {
                if (!flow.destination().internal() && !flow.destination().broadcast()) {
                    result.add(flow);
                    continue;
                }

                perSegment.computeIfAbsent(flow.l4Type() + "|" + Flow.group(flow.destination()), k -> Lists.newArrayList())
                        .add(flow);
            }

            for (List<Flow> segment : perSegment.values()) {
                Endpoint destination = segment.get(0).destination();
                result.addAll(merge(segment, destination.broadcast() ? Endpoint.broadcast(null)
                        : new Endpoint(null, true, destination.segment(), Collections.emptySet(), null, null, null)));
            }
        }

        return result;
    }

    private static int distinctPorts(List<Flow> flows) {
        Set<Integer> ports = Sets.newHashSet();
        for (Flow flow : flows) {
            ports.add(flow.port());
        }

        return ports.size();
    }

    private static List<Flow> merge(List<Flow> flows, @Nullable Endpoint destination) {
        Map<Outcome, Flow> merged = Maps.newLinkedHashMap();
        for (Flow flow : flows) {
            Endpoint target = destination == null ? flow.destination() : destination;
            Flow folded = new Flow(flow.tap(), flow.bucket(), flow.source(), target, flow.sourceKey(),
                    destination == null ? flow.destinationKey() : Flows.key(target), flow.l4Type(),
                    PolicyMatcher.FOLDED_PORT, "", flow.outcome(), flow.sessions(), flow.continued(),
                    flow.firstSeen(), flow.lastSeen());

            merged.merge(flow.outcome(), folded, (a, b) -> a.withSessions(
                    a.sessions() + b.sessions(),
                    a.continued() + b.continued(),
                    a.firstSeen().isBefore(b.firstSeen()) ? a.firstSeen() : b.firstSeen(),
                    a.lastSeen().isAfter(b.lastSeen()) ? a.lastSeen() : b.lastSeen()
            ));
        }

        return Lists.newArrayList(merged.values());
    }

    private static int rank(Outcome outcome) {
        return switch (outcome) {
            case CONNECTED -> 3;
            case REJECTED -> 2;
            case NO_REPLY -> 1;
            case SENT -> 0;
        };
    }

    // A tap that sees only one direction of a connection records it as unanswered, so the better informed tap counts.
    // A tap that reported the stronger outcome itself keeps its weaker reports: it saw both happen.
    static boolean overruled(Outcome outcome, UUID tap, Map<UUID, Outcome> strongestPerTap) {
        if (outcome == Outcome.SENT) {
            return false;
        }

        Outcome own = strongestPerTap.get(tap);
        if (own != null && rank(own) > rank(outcome)) {
            return false;
        }

        for (Map.Entry<UUID, Outcome> other : strongestPerTap.entrySet()) {
            if (!other.getKey().equals(tap) && rank(other.getValue()) > rank(outcome)) {
                return true;
            }
        }

        return false;
    }

    private static class ViolationCollector {

        private static class Pending {
            Policy policy;
            FindingClass findingClass;
            String sourceKey;
            String sourceAddress;
            boolean sourceInternal;
            Long sourceAsn;
            String sourceCountryCode;
            final Set<String> sourceAddresses = Sets.newLinkedHashSet();
            final Set<UUID> taps = Sets.newLinkedHashSet();
            final Map<String, Finding.Target> targets = Maps.newLinkedHashMap();
            final Map<String, Map<UUID, Long>> sessionsPerTap = Maps.newHashMap();
            DateTime firstSeen;
            DateTime lastSeen;
        }

        private final Map<String, Pending> pending = Maps.newLinkedHashMap();
        private final Map<String, Map<UUID, Outcome>> strongest = Maps.newHashMap();
        private long dropped = 0;

        void add(Policy policy,
                 Endpoint source,
                 Endpoint destination,
                 Group group,
                 Outcome outcome,
                 String protocol,
                 boolean continued) {
            FindingClass findingClass = outcome.findingClass();
            String sourceKey = Flows.key(source);

            Pending p = pending.computeIfAbsent(policy.id() + "|" + findingClass + "|" + sourceKey, k -> {
                Pending created = new Pending();
                created.policy = policy;
                created.findingClass = findingClass;
                created.sourceKey = sourceKey;
                created.sourceAddress = source.internal() ? group.source() : null;
                created.sourceInternal = source.internal();
                created.sourceAsn = source.asn();
                created.sourceCountryCode = source.countryCode();
                created.firstSeen = group.firstSeen();
                created.lastSeen = group.lastSeen();
                return created;
            });

            if (!source.internal() && p.sourceAddresses.size() < Findings.MAX_SOURCE_ADDRESSES) {
                p.sourceAddresses.add(group.source());
            }

            p.taps.add(group.tap());
            if (group.firstSeen().isBefore(p.firstSeen)) {
                p.firstSeen = group.firstSeen();
            }
            if (group.lastSeen().isAfter(p.lastSeen)) {
                p.lastSeen = group.lastSeen();
            }

            Finding.Target target = new Finding.Target(group.destination(), destination.internal(),
                    destination.asn(), destination.countryCode(), group.l4Type(), group.port(),
                    protocol.isEmpty() ? null : protocol, outcome, 0, group.firstSeen().getMillis(),
                    group.lastSeen().getMillis());

            Finding.Target known = p.targets.get(target.identity());
            if (known == null) {
                p.targets.put(target.identity(), target);
            } else {
                p.targets.put(target.identity(), new Finding.Target(known.address(), known.internal(), known.asn(),
                        known.countryCode(), known.l4Type(), known.port(), known.protocol(), known.outcome(), 0,
                        Math.min(known.firstSeen(), target.firstSeen()), Math.max(known.lastSeen(), target.lastSeen())));
            }

            // A session that is carried over was counted when it started.
            p.sessionsPerTap.computeIfAbsent(target.identity(), k -> Maps.newHashMap())
                    .merge(group.tap(), continued ? 0 : group.sessions(), Long::sum);

            strongest.computeIfAbsent(connection(policy, sourceKey, target), k -> Maps.newHashMap())
                    .merge(group.tap(), outcome, (a, b) -> rank(a) >= rank(b) ? a : b);
        }

        private static String connection(Policy policy, String sourceKey, Finding.Target target) {
            return policy.id() + "|" + sourceKey + "|" + target.address() + "|" + target.l4Type() + "|" + target.port();
        }

        long dropped() {
            return dropped;
        }

        List<Findings.Violation> violations(int maximumPerPolicy) {
            Map<Long, Integer> perPolicy = Maps.newHashMap();
            List<Findings.Violation> result = Lists.newArrayList();

            for (Pending p : pending.values()) {
                List<Finding.Target> targets = Lists.newArrayList();
                for (Finding.Target target : p.targets.values()) {
                    Map<UUID, Outcome> perTap = strongest.get(connection(p.policy, p.sourceKey, target));

                    // Where several taps saw the same connections, the tap that saw the most counts.
                    long sessions = -1;
                    for (Map.Entry<UUID, Long> counted : p.sessionsPerTap.get(target.identity()).entrySet()) {
                        if (!overruled(target.outcome(), counted.getKey(), perTap)) {
                            sessions = Math.max(sessions, counted.getValue());
                        }
                    }

                    if (sessions < 0) {
                        continue;
                    }

                    targets.add(new Finding.Target(target.address(), target.internal(), target.asn(),
                            target.countryCode(), target.l4Type(), target.port(), target.protocol(), target.outcome(),
                            sessions, target.firstSeen(), target.lastSeen()));
                }

                if (targets.isEmpty()) {
                    continue;
                }

                int count = perPolicy.merge(p.policy.id(), 1, Integer::sum);
                if (count > maximumPerPolicy) {
                    dropped++;
                    continue;
                }

                result.add(new Findings.Violation(p.policy, p.findingClass, p.sourceKey, p.sourceAddress,
                        p.sourceInternal, p.sourceAsn, p.sourceCountryCode, p.sourceAddresses, targets, p.taps,
                        p.firstSeen, p.lastSeen));
            }

            return result;
        }

    }

    private void restamp(UUID organizationId, UUID tenantId) {
        EngineState before = segmentation.state(organizationId, tenantId);
        if (!before.restampPending()) {
            return;
        }

        try (Timer.Context ignored = restampTimer.time()) {
            nzyme.getDatabase().useTransaction(handle -> {
                if (!lock(handle, organizationId, tenantId)) {
                    return;
                }

                EngineState state = segmentation.state(handle, organizationId, tenantId, true);
                if (!state.restampPending()) {
                    return;
                }

                MembershipResolver resolver = segmentation.buildResolver(handle, organizationId, tenantId,
                        Collections.emptyList(), null);

                restampAddresses(handle, organizationId, tenantId, resolver, "source");
                restampAddresses(handle, organizationId, tenantId, resolver, "destination");
                restampNetworks(handle, organizationId, tenantId, resolver, "source");
                restampNetworks(handle, organizationId, tenantId, resolver, "destination");

                // Flows that now belong to a segment may be attempts that the segment excludes.
                segmentation.exclusions().findAllPerSegment(handle, organizationId, tenantId).forEach((segment, exclusions) -> {
                    for (Exclusion exclusion : exclusions) {
                        segmentation.flows().deleteUnanswered(handle, organizationId, tenantId, segment,
                                exclusion.destination());
                    }
                });

                segmentation.flows().rebuildCrossings(handle, organizationId, tenantId);

                handle.createUpdate("UPDATE segmentation_engine_state SET restamp_completed_at = NOW(), " +
                                "updated_at = NOW() WHERE organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute();
            });
        }

        segmentation.invalidateResolver(organizationId, tenantId);
    }

    private record Stamp(UUID tap, String address, boolean internal, @Nullable Long segmentId) { }

    private void restampAddresses(Handle handle,
                                  UUID organizationId,
                                  UUID tenantId,
                                  MembershipResolver resolver,
                                  String side) {
        List<Stamp> stamps = handle.createQuery("SELECT DISTINCT tap_uuid, host(" + side + "_address) AS address, " +
                        side + "_internal AS internal, " + side + "_segment_id AS segment_id " +
                        "FROM segmentation_flows WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND " + side + "_address IS NOT NULL " +
                        "AND " + side + "_key NOT LIKE '" + Flows.BROADCAST_KEYS + "'")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> {
                    long segmentId = rs.getLong("segment_id");
                    return new Stamp(UUID.fromString(rs.getString("tap_uuid")), rs.getString("address"),
                            rs.getBoolean("internal"), rs.wasNull() ? null : segmentId);
                })
                .list();

        PreparedBatch batch = handle.prepareBatch("UPDATE segmentation_flows SET " + side + "_segment_id = :segment_id, " +
                side + "_internal = :internal WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                "AND " + side + "_address = :address::inet AND tap_uuid = :tap_uuid " +
                "AND " + side + "_key NOT LIKE '" + Flows.BROADCAST_KEYS + "'");

        // An address has one row per stamp it was stored with. Each is updated once.
        Set<String> updated = Sets.newHashSet();
        for (Stamp stamp : stamps) {
            InetAddress address = InetAddresses.forString(stamp.address());
            Segment segment = resolver.segmentOf(stamp.tap(), address);

            Long segmentId = segment == null ? null : segment.id();
            boolean internal = segment == null ? address.isSiteLocalAddress() : segment.kind() == SegmentKind.INTERNAL;

            if (java.util.Objects.equals(segmentId, stamp.segmentId()) && internal == stamp.internal()) {
                continue;
            }

            if (!updated.add(stamp.tap() + "|" + stamp.address())) {
                continue;
            }

            batch.bind("segment_id", segmentId)
                    .bind("internal", internal)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("address", stamp.address())
                    .bind("tap_uuid", stamp.tap())
                    .add();
        }

        if (batch.size() > 0) {
            batch.execute();
        }
    }

    // What was stored per autonomous system cannot be matched against the address ranges of an external network
    // afterwards.
    private void restampNetworks(Handle handle,
                                 UUID organizationId,
                                 UUID tenantId,
                                 MembershipResolver resolver,
                                 String side) {
        Collection<Segment> segments = resolver.segments();

        List<Long> ids = segments.stream().map(Segment::id).collect(Collectors.toList());
        String known = ids.isEmpty() ? "" : "AND " + side + "_segment_id NOT IN (<ids>) ";
        var forget = handle.createUpdate("UPDATE segmentation_flows SET " + side + "_segment_id = NULL " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND " + side + "_address IS NULL AND " + side + "_segment_id IS NOT NULL " + known)
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId);
        if (!ids.isEmpty()) {
            forget = forget.bindList("ids", ids);
        }
        forget.execute();

        // Flows that were stored per autonomous system lose and gain their external network by its definition.
        handle.createUpdate("UPDATE segmentation_flows SET " + side + "_segment_id = NULL " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                        "AND " + side + "_address IS NULL AND NOT " + side + "_internal " +
                        "AND " + side + "_segment_id IS NOT NULL AND " + side + "_key NOT LIKE 'seg:%'")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();

        for (Segment segment : segments) {
            if (segment.kind() != SegmentKind.EXTERNAL) {
                continue;
            }

            for (Segment.Member member : segment.members()) {
                if (member.type() != Segment.MemberType.ASN || member.asn() == null) {
                    continue;
                }

                handle.createUpdate("UPDATE segmentation_flows SET " + side + "_segment_id = :segment_id " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND " + side + "_address IS NULL AND NOT " + side + "_internal " +
                                "AND " + side + "_segment_id IS NULL AND " + side + "_asn = :asn")
                        .bind("segment_id", segment.id())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("asn", member.asn())
                        .execute();
            }
        }
    }

}
