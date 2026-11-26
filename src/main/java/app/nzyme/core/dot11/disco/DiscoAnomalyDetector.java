package app.nzyme.core.dot11.disco;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.signal.FrameSignalAnalysis;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class DiscoAnomalyDetector {

    private static final Logger LOG = LogManager.getLogger(DiscoAnomalyDetector.class);

    private static final int EVALUATION_MINUTES = 5;
    private static final int INGEST_DELAY_SECONDS = 20;

    private static final int MAX_CATCH_UP_MINUTES = 30;
    private static final String EVALUATED_UNTIL_KEY = "dot11_disco_detection_evaluated_until";

    private static final int STATEMENT_TIMEOUT_MS = 20000;

    public static final int MERGE_GAP_MINUTES = 15;

    private static final int MAX_OBSERVATIONS_PER_RULE = 5000;

    private static final int MAX_PAIRS_PER_ACCESS_POINT = 10;

    public static final int MAX_NEW_ANOMALIES_PER_RUN = 200;
    private static final int MAX_REASON_CODES = 32;
    public static final int MAX_SAMPLE_CLIENTS = 25;

    private static final int SECURITY_LOOKBACK_MINUTES = 30;
    private static final int MIRROR_SECONDS = 5;
    private static final int CONNECTING_SECONDS = 60;

    private static final String PMF_REQUIRED = "[{\"pmf\": \"Required\"}]";
    private static final String EAP_KEY_MANAGEMENT =
            "$[*].suites.key_management_modes[*] ? (@ starts with \"DOT1X\")";

    private static final String FIRST_SEEN = "COALESCE(first_seen, created_at)";
    private static final String LAST_SEEN = "COALESCE(last_seen, created_at)";
    private static final String REASON_CODES = "(array_agg(DISTINCT reason_code) FILTER (WHERE reason_code IS NOT NULL))" +
            "[1 : " + MAX_REASON_CODES + "]";

    private static final String MONITORED_FIRST = "(bssid = ANY(:monitored)) DESC";

    private final NzymeNode nzyme;
    private final Timer runTimer;

    public DiscoAnomalyDetector(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.runTimer = nzyme.getMetrics().timer(MetricNames.DOT11_DISCO_ANOMALY_DETECTION_RUN_TIMER);
    }

    private record Key(UUID tapUuid, DiscoAnomalyType type, String bssid, @Nullable String client) { }

    private record Active(long id, UUID uuid, DateTime evaluatedUntil) { }

    private static final class Observation {
        private final Key key;
        private final DateTime minute;
        private DateTime firstSeen;
        private DateTime lastSeen;
        private long frames;
        private long framesFromBssid;
        @Nullable
        private Integer peak;
        @Nullable
        private Integer threshold;
        @Nullable
        private Double signal;
        @Nullable
        private Double referenceSignal;
        private final Set<Integer> reasonCodes = Sets.newTreeSet();
        private final Set<String> frameTypes = Sets.newTreeSet();
        private final Set<String> clients = Sets.newTreeSet();

        private Observation(Key key, DateTime minute, DateTime firstSeen, DateTime lastSeen) {
            this.key = key;
            this.minute = minute;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    private static final class Observations {
        private final Map<Key, Map<DateTime, Observation>> byKey = Maps.newHashMap();

        private Observation of(UUID tapUuid, DiscoAnomalyType type, String bssid, @Nullable String client,
                               DateTime seenAt, DateTime firstSeen, DateTime lastSeen) {
            Key key = new Key(tapUuid, type, bssid, client);
            DateTime minute = seenAt.withSecondOfMinute(0).withMillisOfSecond(0);

            Observation observation = byKey.computeIfAbsent(key, k -> Maps.newTreeMap())
                    .computeIfAbsent(minute, m -> new Observation(key, m, firstSeen, lastSeen));

            if (firstSeen.isBefore(observation.firstSeen)) {
                observation.firstSeen = firstSeen;
            }
            if (lastSeen.isAfter(observation.lastSeen)) {
                observation.lastSeen = lastSeen;
            }

            return observation;
        }

        // The minutes of one anomaly have to stay in order.
        private List<Observation> ordered(Set<String> monitored) {
            List<Observation> all = Lists.newArrayList();
            byKey.values().forEach(minutes -> all.addAll(minutes.values()));
            all.sort(Comparator.comparing((Observation o) -> !monitored.contains(o.key.bssid()))
                    .thenComparing(o -> o.minute));

            return all;
        }
    }

    public void runForTenant(UUID organizationId, UUID tenantId) {
        Map<UUID, Tap> taps = Maps.newHashMap();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)) {
            taps.put(tap.uuid(), tap);
        }

        if (taps.isEmpty()) {
            return;
        }

        DateTime until = DateTime.now().minusSeconds(INGEST_DELAY_SECONDS)
                .withSecondOfMinute(0).withMillisOfSecond(0);
        DateTime from = evaluationStart(until, organizationId, tenantId);

        DiscoDetectionConfiguration config = nzyme.getDot11().discoAnomalies()
                .getConfiguration(organizationId, tenantId);

        Map<String, List<MonitoredSSID>> monitored = nzyme.getDot11().discoAnomalies()
                .monitoredNetworksByBssid(organizationId, tenantId, true);

        if (!config.anyEnabled() || (config.monitoredNetworksOnly() && monitored.isEmpty())
                || !hasFrames(Lists.newArrayList(taps.keySet()), from, until)) {
            markEvaluated(until, organizationId, tenantId);
            return;
        }

        try (Timer.Context ignored = runTimer.time()) {
            boolean evaluated = nzyme.getDatabase().inTransaction(handle -> {
                boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "dot11_disco_anomalies:" + organizationId + ":" + tenantId)
                        .mapTo(Boolean.class)
                        .one();

                if (!locked) {
                    LOG.debug("Disconnection anomaly detection for tenant [{}/{}] is running on another node. " +
                            "Skipping.", organizationId, tenantId);
                    return false;
                }

                handle.execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);

                Scope scope = new Scope(handle, Lists.newArrayList(taps.keySet()), from, until,
                        monitored.keySet().toArray(new String[0]), config.monitoredNetworksOnly());

                Observations observations = new Observations();
                if (config.unprotectedFrameEnabled()) {
                    unprotectedFrames(scope, config, observations);
                }
                if (config.mirroredFramesEnabled()) {
                    mirroredFrames(scope, observations);
                }
                if (config.repeatedFramesEnabled()) {
                    repeatedFrames(scope, config, observations);
                }
                if (config.signalMismatchEnabled()) {
                    signalMismatches(scope, config, observations);
                }
                if (config.authenticationFloodEnabled()) {
                    authenticationFloods(scope, config, observations);
                }
                if (config.eapolFloodEnabled()) {
                    eapolFloods(scope, config, observations);
                }
                if (config.eapFailureEnabled()) {
                    eapFailures(scope, config, observations);
                }
                if (config.frameOverflowEnabled()) {
                    frameOverflows(scope, observations);
                }

                apply(handle, observations, organizationId, tenantId, taps, monitored);

                return true;
            });

            if (evaluated) {
                markEvaluated(until, organizationId, tenantId);
            }
        }
    }

    private DateTime evaluationStart(DateTime until, UUID organizationId, UUID tenantId) {
        DateTime from = until.minusMinutes(EVALUATION_MINUTES);

        Optional<DateTime> evaluatedUntil = nzyme.getDatabaseCoreRegistry()
                .getValue(EVALUATED_UNTIL_KEY, organizationId, tenantId)
                .map(value -> new DateTime(Long.parseLong(value)));

        if (evaluatedUntil.isEmpty() || !evaluatedUntil.get().isBefore(from)) {
            return from;
        }

        DateTime earliest = until.minusMinutes(MAX_CATCH_UP_MINUTES);
        if (evaluatedUntil.get().isBefore(earliest)) {
            LOG.warn("Disconnection anomaly detection of tenant [{}/{}] did not run between [{}] and [{}]. Frames of " +
                    "that time are not evaluated.", organizationId, tenantId, evaluatedUntil.get(), earliest);
            return earliest;
        }

        return evaluatedUntil.get();
    }

    private void markEvaluated(DateTime until, UUID organizationId, UUID tenantId) {
        nzyme.getDatabaseCoreRegistry()
                .setValue(EVALUATED_UNTIL_KEY, String.valueOf(until.getMillis()), organizationId, tenantId);
    }

    private boolean hasFrames(List<UUID> taps, DateTime from, DateTime until) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS (SELECT 1 FROM dot11_disco_frames WHERE tap_uuid IN (<taps>) " +
                                "AND created_at >= :from AND created_at < :until) OR EXISTS (SELECT 1 " +
                                "FROM dot11_connection_frames WHERE tap_uuid IN (<taps>) " +
                                "AND created_at >= :from AND created_at < :until) OR EXISTS (SELECT 1 " +
                                "FROM dot11_frame_overflows WHERE tap_uuid IN (<taps>) " +
                                "AND created_at >= :from AND created_at < :until)")
                        .bindList("taps", taps)
                        .bind("from", from)
                        .bind("until", until)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    private record Scope(Handle handle, List<UUID> taps, DateTime from, DateTime until, String[] monitored,
                         boolean monitoredOnly) {

        private Query query(String sql) {
            return handle.createQuery(sql)
                    .define("monitored_only", monitoredOnly ? "AND bssid = ANY(:monitored) " : "")
                    .bindList("taps", taps)
                    .bind("from", from)
                    .bind("until", until)
                    .bind("monitored", monitored)
                    .bind("limit", MAX_OBSERVATIONS_PER_RULE);
        }

        private Query ranked(String commonTableExpressions, String observed, List<String> pairColumns,
                             String timeColumn) {
            String ofObserved = pairColumns.stream().map(c -> "o." + c).collect(Collectors.joining(", "));
            String ofSized = pairColumns.stream().map(c -> "s." + c).collect(Collectors.joining(", "));

            return query("WITH " + commonTableExpressions + "observed AS (" + observed + "), " +
                    "sized AS (SELECT o.*, SUM(o.frames) OVER (PARTITION BY o.tap_uuid, o.bssid, " + ofObserved +
                    ") AS pair_frames FROM observed o), " +
                    "ranked AS (SELECT s.*, dense_rank() OVER (PARTITION BY s.tap_uuid, s.bssid " +
                    "ORDER BY s.pair_frames DESC, " + ofSized + ") AS pair_rank FROM sized s) " +
                    "SELECT * FROM ranked WHERE pair_rank <= :pairs_per_access_point " +
                    "ORDER BY (bssid = ANY(:monitored)) DESC, pair_rank, tap_uuid, bssid, " + timeColumn + " DESC " +
                    "LIMIT :limit")
                    .bind("pairs_per_access_point", MAX_PAIRS_PER_ACCESS_POINT);
        }

    }

    private void warnAtLimit(String rule, int rows) {
        if (rows >= MAX_OBSERVATIONS_PER_RULE) {
            LOG.warn("Disconnection anomaly detection rule [{}] reached its limit of <{}> rows in one run. " +
                    "More were left out.", rule, MAX_OBSERVATIONS_PER_RULE);
        }
    }

    private void unprotectedFrames(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        // With the Order bit set, stations disagree about where the reason code is, so the code the tap read
        // is not necessarily the one the receiver acted on.
        String ignoredReasons = config.unprotectedFrameIgnoreMissingState()
                ? "AND (d.reason_code IS NULL OR d.reason_code NOT IN (6, 7) OR d.order_frames > 0) " : "";

        // Frames and connection frames are summed up per pair and report before they are matched, so that many
        // rows of one pair cannot multiply each other.
        int rows = scope.ranked("candidates AS (SELECT d.* FROM dot11_disco_frames d WHERE d.tap_uuid IN (<taps>) " +
                        "AND d.created_at >= :from AND d.created_at < :until AND d.is_protected = false " +
                        "AND (d.transmitter = d.bssid OR d.receiver = d.bssid) " +
                        "AND (d.receiver <> :broadcast OR d.integrity_frames < d.frame_count) " +
                        ignoredReasons + "<monitored_only>), " +
                        "protected_networks AS (SELECT DISTINCT b.bssid FROM dot11_bssids b " +
                        "JOIN dot11_ssids s ON s.bssid_id = b.id WHERE b.tap_uuid IN (<taps>) " +
                        "AND b.created_at >= :security_from AND b.created_at < :until " +
                        "AND b.bssid IN (SELECT DISTINCT bssid FROM candidates) " +
                        "AND s.security_settings @> CAST(:pmf_required AS jsonb)), " +
                        "pairs AS (SELECT tap_uuid, created_at, bssid, transmitter, receiver, " +
                        "SUM(CASE WHEN receiver = :broadcast THEN frame_count - integrity_frames ELSE frame_count END) AS frames, " +
                        "MIN(" + FIRST_SEEN + ") AS first_seen, MAX(" + LAST_SEEN + ") AS last_seen, " +
                        REASON_CODES + " AS reason_codes, array_agg(DISTINCT disco_type) AS frame_types " +
                        "FROM candidates WHERE bssid IN (SELECT bssid FROM protected_networks) " +
                        "GROUP BY tap_uuid, created_at, bssid, transmitter, receiver), " +
                        "connecting AS (SELECT c.tap_uuid, c.created_at, c.bssid, " +
                        "LEAST(c.transmitter, c.receiver) AS station_a, GREATEST(c.transmitter, c.receiver) AS station_b, " +
                        "MIN(c.first_seen) AS first_seen, MAX(c.last_seen) AS last_seen, " +
                        "bool_or(c.frame_type IN ('HANDSHAKE_3', 'HANDSHAKE_4')) AS completed " +
                        "FROM dot11_connection_frames c WHERE c.tap_uuid IN (<taps>) " +
                        "AND c.created_at >= :connecting_from AND c.created_at <= :connecting_until " +
                        "AND c.bssid IN (SELECT DISTINCT bssid FROM pairs) " +
                        "GROUP BY c.tap_uuid, c.created_at, c.bssid, LEAST(c.transmitter, c.receiver), " +
                        "GREATEST(c.transmitter, c.receiver)), " +
                        "explained AS (SELECT p.tap_uuid, p.created_at, p.bssid, p.transmitter, p.receiver, " +
                        "bool_or(c.completed) AS completed FROM pairs p JOIN connecting c " +
                        "ON c.tap_uuid = p.tap_uuid AND c.bssid = p.bssid " +
                        "AND c.station_a = LEAST(p.transmitter, p.receiver) " +
                        "AND c.station_b = GREATEST(p.transmitter, p.receiver) " +
                        "AND c.created_at >= p.created_at - make_interval(secs => :connecting_seconds + 30) " +
                        "AND c.created_at <= p.created_at + interval '30 seconds' " +
                        "AND c.first_seen <= p.last_seen " +
                        "AND c.last_seen >= p.first_seen - make_interval(secs => :connecting_seconds) " +
                        "GROUP BY p.tap_uuid, p.created_at, p.bssid, p.transmitter, p.receiver), ",
                        "SELECT p.* FROM pairs p LEFT JOIN explained e ON e.tap_uuid = p.tap_uuid " +
                        "AND e.created_at = p.created_at AND e.bssid = p.bssid AND e.transmitter = p.transmitter " +
                        "AND e.receiver = p.receiver WHERE e.tap_uuid IS NULL OR e.completed IS TRUE",
                        List.of("transmitter", "receiver"), "created_at")
                .bind("broadcast", Dot11.BROADCAST_MAC)
                .bind("security_from", scope.from().minusMinutes(SECURITY_LOOKBACK_MINUTES))
                .bind("pmf_required", PMF_REQUIRED)
                .bind("connecting_seconds", CONNECTING_SECONDS)
                .bind("connecting_from", scope.from().minusSeconds(CONNECTING_SECONDS + 30))
                .bind("connecting_until", scope.until().plusSeconds(30))
                .map((rs, ctx) -> {
                    pairObservation(rs, DiscoAnomalyType.UNPROTECTED_FRAME, observations);
                    return 1;
                })
                .list().size();

        warnAtLimit("unprotected frames", rows);
    }

    private void mirroredFrames(Scope scope, Observations observations) {
        int rows = scope.ranked("pairs AS (SELECT tap_uuid, created_at, bssid, transmitter, receiver, " +
                        "SUM(frame_count) AS frames, MIN(" + FIRST_SEEN + ") AS first_seen, " +
                        "MAX(" + LAST_SEEN + ") AS last_seen, " + REASON_CODES + " AS reason_codes, " +
                        "array_agg(DISTINCT disco_type) AS frame_types FROM dot11_disco_frames " +
                        "WHERE tap_uuid IN (<taps>) AND created_at >= :lookback AND created_at < :lookahead " +
                        "AND receiver <> :broadcast AND transmitter <> receiver " +
                        "AND (transmitter = bssid OR receiver = bssid) <monitored_only>" +
                        "GROUP BY tap_uuid, created_at, bssid, transmitter, receiver), ",
                        "SELECT p.* FROM pairs p WHERE p.created_at >= :from AND p.created_at < :until " +
                        "AND EXISTS (SELECT 1 FROM pairs m WHERE m.tap_uuid = p.tap_uuid AND m.bssid = p.bssid " +
                        "AND m.transmitter = p.receiver AND m.receiver = p.transmitter " +
                        "AND m.last_seen >= p.first_seen - make_interval(secs => :mirror_seconds) " +
                        "AND m.first_seen <= p.last_seen + make_interval(secs => :mirror_seconds))",
                        List.of("transmitter", "receiver"), "created_at")
                .bind("broadcast", Dot11.BROADCAST_MAC)
                .bind("lookback", scope.from().minusSeconds(30))
                .bind("lookahead", scope.until().plusSeconds(INGEST_DELAY_SECONDS))
                .bind("mirror_seconds", MIRROR_SECONDS)
                .map((rs, ctx) -> {
                    pairObservation(rs, DiscoAnomalyType.MIRRORED_FRAMES, observations);
                    return 1;
                })
                .list().size();

        warnAtLimit("mirrored frames", rows);
    }

    private void repeatedFrames(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        // Two adapters record the same frame on neighboring frequencies. The frame threshold counts one frequency.
        int rows = scope.ranked("",
                        "SELECT tap_uuid, created_at, bssid, transmitter, receiver, SUM(frame_count) AS frames, " +
                        "MAX(pair_sequence_numbers) AS peak, MIN(" + FIRST_SEEN + ") AS first_seen, " +
                        "MAX(" + LAST_SEEN + ") AS last_seen, " + REASON_CODES + " AS reason_codes, " +
                        "array_agg(DISTINCT disco_type) AS frame_types FROM (SELECT d.*, SUM(d.frame_count) OVER (" +
                        "PARTITION BY d.tap_uuid, d.created_at, d.bssid, d.transmitter, d.receiver, d.frequency) " +
                        "AS frequency_frames FROM dot11_disco_frames d WHERE d.tap_uuid IN (<taps>) " +
                        "AND d.created_at >= :from AND d.created_at < :until <monitored_only>) d " +
                        "GROUP BY tap_uuid, created_at, bssid, transmitter, receiver " +
                        "HAVING MAX(pair_sequence_numbers) >= :minimum_sequence_numbers " +
                        "OR MAX(frequency_frames) >= :minimum_frames",
                        List.of("transmitter", "receiver"), "created_at")
                .bind("minimum_sequence_numbers", config.repeatedFramesMinimumSequenceNumbers())
                .bind("minimum_frames", config.repeatedFramesMinimumFrames())
                .map((rs, ctx) -> {
                    Observation o = pairObservation(rs, DiscoAnomalyType.REPEATED_FRAMES, observations);
                    o.peak = max(o.peak, rs.getInt("peak"));
                    o.threshold = config.repeatedFramesMinimumSequenceNumbers();
                    return 1;
                })
                .list().size();

        warnAtLimit("repeated frames", rows);
    }

    private void signalMismatches(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        int rows = scope.ranked("frames AS (SELECT d.tap_uuid, d.created_at, d.bssid, d.receiver, d.frequency, " +
                        "d.frame_count, d.signal_strength_average, d.signal_histogram, d.reason_code, d.disco_type, " +
                        "COALESCE(d.first_seen, d.created_at) AS seen_first, " +
                        "COALESCE(d.last_seen, d.created_at) AS seen_last FROM dot11_disco_frames d " +
                        "WHERE d.tap_uuid IN (<taps>) AND d.created_at >= :from AND d.created_at < :until " +
                        "AND d.transmitter = d.bssid AND d.frequency IS NOT NULL " +
                        "AND d.signal_strength_average IS NOT NULL <monitored_only>), " +
                        FrameSignalAnalysis.beaconsCte("{tap} IN (<taps>) " +
                                "AND {bssid} IN (SELECT DISTINCT bssid FROM frames)") + ", " +
                        FrameSignalAnalysis.BEACON_MINUTES + ", " +
                        "compared AS (SELECT f.tap_uuid, f.created_at, f.bssid, f.frequency, " +
                        "SUM(r.signal_sum)::float / SUM(r.frames) AS reference " +
                        "FROM (SELECT DISTINCT tap_uuid, created_at, bssid, frequency FROM frames) f " +
                        "JOIN beacon_minutes r ON r.tap_uuid = f.tap_uuid AND r.bssid = f.bssid " +
                        "AND r.frequency = f.frequency AND r.minute <= date_trunc('minute', f.created_at) " +
                        "AND r.minute >= date_trunc('minute', f.created_at) - make_interval(mins => :reference_minutes) " +
                        "GROUP BY f.tap_uuid, f.created_at, f.bssid, f.frequency " +
                        "HAVING SUM(r.frames) >= :minimum_reference_frames), " +
                        "mismatches AS (SELECT f.tap_uuid, f.created_at, f.bssid, f.receiver, f.frequency, " +
                        "f.seen_first, f.seen_last, f.reason_code, f.disco_type, c.reference, v.signal, " +
                        "v.frames AS signal_frames FROM frames f JOIN compared c ON c.tap_uuid = f.tap_uuid " +
                        "AND c.created_at = f.created_at AND c.bssid = f.bssid AND c.frequency = f.frequency " +
                        "CROSS JOIN LATERAL (SELECT e.key::float AS signal, e.value::bigint AS frames " +
                        "FROM jsonb_each_text(f.signal_histogram) e UNION ALL " +
                        "SELECT f.signal_strength_average::float, f.frame_count " +
                        "WHERE f.signal_histogram IS NULL OR f.signal_histogram = '{}'::jsonb) v " +
                        "WHERE ABS(v.signal - c.reference) >= :minimum_difference), ",
                        "SELECT tap_uuid, created_at, bssid, receiver, SUM(signal_frames) AS frames, " +
                        "SUM(signal * signal_frames)::float / SUM(signal_frames) AS signal, " +
                        "MIN(seen_first) AS first_seen, MAX(seen_last) AS last_seen, " + REASON_CODES +
                        " AS reason_codes, array_agg(DISTINCT disco_type) AS frame_types, " +
                        "MIN(reference) AS reference FROM mismatches " +
                        "GROUP BY tap_uuid, created_at, bssid, receiver, frequency",
                        List.of("receiver"), "created_at")
                .bind("reference_from", scope.from().minusMinutes(FrameSignalAnalysis.REFERENCE_MINUTES + 1))
                .bind("reference_to", scope.until())
                .bind("reference_minutes", FrameSignalAnalysis.REFERENCE_MINUTES)
                .bind("minimum_reference_frames", FrameSignalAnalysis.MINIMUM_REFERENCE_FRAMES)
                .bind("minimum_difference", config.signalMismatchMinimumDifference())
                .map((rs, ctx) -> {
                    String receiver = rs.getString("receiver");
                    Observation o = observations.of(uuid(rs), DiscoAnomalyType.SIGNAL_MISMATCH, rs.getString("bssid"),
                            Dot11.BROADCAST_MAC.equals(receiver) ? null : receiver,
                            time(rs, "created_at"), time(rs, "first_seen"), time(rs, "last_seen"));
                    long frames = rs.getLong("frames");
                    o.frames += frames;
                    o.framesFromBssid += frames;
                    o.threshold = config.signalMismatchMinimumDifference();
                    o.reasonCodes.addAll(integers(rs, "reason_codes"));
                    o.frameTypes.addAll(strings(rs, "frame_types"));

                    double signal = rs.getDouble("signal");
                    double reference = rs.getDouble("reference");
                    int difference = (int) Math.round(Math.abs(signal - reference));
                    if (o.peak == null || difference > o.peak) {
                        o.peak = difference;
                        o.signal = signal;
                        o.referenceSignal = reference;
                    }

                    return 1;
                })
                .list().size();

        warnAtLimit("signal strength mismatch", rows);
    }

    private void authenticationFloods(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        int minimum = config.authenticationFloodMinimumTransmitters();

        int rows = scope.query("SELECT tap_uuid, date_trunc('minute', created_at) AS minute, bssid, " +
                        "COUNT(DISTINCT transmitter) AS peak, SUM(frame_count) AS frames, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "(array_agg(DISTINCT transmitter))[1 : " + MAX_SAMPLE_CLIENTS + "] AS clients, " +
                        "array_agg(DISTINCT frame_type) AS frame_types FROM dot11_connection_frames " +
                        "WHERE tap_uuid IN (<taps>) AND created_at >= :from AND created_at < :until " +
                        "AND frame_type = 'AUTHENTICATION' AND receiver = bssid <monitored_only>" +
                        "GROUP BY tap_uuid, date_trunc('minute', created_at), bssid " +
                        "HAVING COUNT(DISTINCT transmitter) >= :minimum " +
                        "ORDER BY " + MONITORED_FIRST + ", minute DESC, peak DESC LIMIT :limit")
                .bind("minimum", minimum)
                .map((rs, ctx) -> {
                    Observation o = observations.of(uuid(rs), DiscoAnomalyType.AUTHENTICATION_FLOOD,
                            rs.getString("bssid"), null,
                            time(rs, "minute"), time(rs, "first_seen"), time(rs, "last_seen"));
                    o.frames += rs.getLong("frames");
                    o.peak = max(o.peak, rs.getInt("peak"));
                    o.threshold = minimum;
                    o.frameTypes.addAll(strings(rs, "frame_types"));
                    o.clients.addAll(strings(rs, "clients"));
                    return 1;
                })
                .list().size();

        warnAtLimit("authentication flood", rows);
    }

    private void eapolFloods(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        int rows = scope.query("SELECT tap_uuid, date_trunc('minute', created_at) AS minute, bssid, frame_type, " +
                        "SUM(GREATEST(frame_count - retry_frames, 1)) AS peak, SUM(frame_count) AS frames, " +
                        "COALESCE(SUM(frame_count) FILTER (WHERE transmitter = bssid), 0) AS frames_from_bssid, " +
                        "MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "(array_agg(DISTINCT CASE WHEN transmitter = bssid THEN receiver ELSE transmitter END))" +
                        "[1 : " + MAX_SAMPLE_CLIENTS + "] AS clients FROM dot11_connection_frames " +
                        "WHERE tap_uuid IN (<taps>) AND created_at >= :from AND created_at < :until " +
                        "AND frame_type IN ('EAPOL_START', 'EAPOL_LOGOFF') <monitored_only>" +
                        "GROUP BY tap_uuid, date_trunc('minute', created_at), bssid, frame_type " +
                        "HAVING SUM(GREATEST(frame_count - retry_frames, 1)) >= :minimum " +
                        "ORDER BY " + MONITORED_FIRST + ", minute DESC, peak DESC LIMIT :limit")
                .bind("minimum", config.eapolFloodMinimumFrames())
                .map((rs, ctx) -> {
                    Observation o = observations.of(uuid(rs), DiscoAnomalyType.EAPOL_FLOOD, rs.getString("bssid"),
                            null, time(rs, "minute"), time(rs, "first_seen"), time(rs, "last_seen"));
                    o.frames += rs.getLong("frames");
                    o.framesFromBssid += rs.getLong("frames_from_bssid");
                    o.peak = max(o.peak, capped(rs.getLong("peak")));
                    o.threshold = config.eapolFloodMinimumFrames();
                    o.frameTypes.add(rs.getString("frame_type"));
                    o.clients.addAll(strings(rs, "clients"));
                    return 1;
                })
                .list().size();

        warnAtLimit("EAPOL flood", rows);
    }

    private void eapFailures(Scope scope, DiscoDetectionConfiguration config, Observations observations) {
        // WPS runs over EAP and always ends with a failure message, so it counts as a network that uses EAP.
        // A network nothing is known about is treated like one that does.
        int rows = scope.ranked("failures AS (SELECT c.* FROM dot11_connection_frames c WHERE c.tap_uuid IN (<taps>) " +
                        "AND c.created_at >= :from AND c.created_at < :until AND c.frame_type = 'EAP_FAILURE' " +
                        "AND c.transmitter = c.bssid " +
                        (scope.monitoredOnly() ? "AND c.bssid = ANY(:monitored)" : "") + "), " +
                        "networks AS (SELECT b.bssid, bool_or(true = ANY(s.has_wps) " +
                        "OR jsonb_path_exists(s.security_settings, CAST(:eap_key_management AS jsonpath))) AS uses_eap " +
                        "FROM dot11_bssids b JOIN dot11_ssids s ON s.bssid_id = b.id WHERE b.tap_uuid IN (<taps>) " +
                        "AND b.created_at >= :security_from AND b.created_at < :until " +
                        "AND b.bssid IN (SELECT DISTINCT bssid FROM failures) GROUP BY b.bssid), ",
                        "SELECT c.tap_uuid, date_trunc('minute', c.created_at) AS minute, c.bssid, c.receiver, " +
                        "SUM(GREATEST(c.frame_count - c.retry_frames, 1)) AS peak, SUM(c.frame_count) AS frames, " +
                        "MIN(c.first_seen) AS first_seen, MAX(c.last_seen) AS last_seen, " +
                        "COALESCE(bool_or(n.uses_eap), true) AS uses_eap " +
                        "FROM failures c LEFT JOIN networks n ON n.bssid = c.bssid " +
                        "GROUP BY c.tap_uuid, date_trunc('minute', c.created_at), c.bssid, c.receiver " +
                        "HAVING SUM(GREATEST(c.frame_count - c.retry_frames, 1)) >= " +
                        "CASE WHEN COALESCE(bool_or(n.uses_eap), true) THEN :minimum ELSE 1 END",
                        List.of("receiver"), "minute")
                .bind("security_from", scope.from().minusMinutes(SECURITY_LOOKBACK_MINUTES))
                .bind("eap_key_management", EAP_KEY_MANAGEMENT)
                .bind("minimum", config.eapFailureMinimumFailures())
                .map((rs, ctx) -> {
                    String receiver = rs.getString("receiver");
                    Observation o = observations.of(uuid(rs), DiscoAnomalyType.EAP_FAILURE, rs.getString("bssid"),
                            Dot11.BROADCAST_MAC.equals(receiver) ? null : receiver,
                            time(rs, "minute"), time(rs, "first_seen"), time(rs, "last_seen"));
                    o.frames += rs.getLong("frames");
                    o.framesFromBssid += rs.getLong("frames");
                    o.peak = max(o.peak, capped(rs.getLong("peak")));
                    // A threshold of 0 marks a network that does not use EAP at all.
                    o.threshold = rs.getBoolean("uses_eap") ? config.eapFailureMinimumFailures() : 0;
                    o.frameTypes.add("EAP_FAILURE");
                    return 1;
                })
                .list().size();

        warnAtLimit("EAP failure", rows);
    }

    private void frameOverflows(Scope scope, Observations observations) {
        int rows = scope.query("SELECT tap_uuid, date_trunc('minute', created_at) AS minute, bssid, " +
                        "SUM(frame_count) AS frames, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen, " +
                        "array_agg(DISTINCT frame_type) AS frame_types FROM dot11_frame_overflows " +
                        "WHERE tap_uuid IN (<taps>) AND created_at >= :from AND created_at < :until <monitored_only>" +
                        "GROUP BY tap_uuid, date_trunc('minute', created_at), bssid " +
                        "ORDER BY " + MONITORED_FIRST + ", minute DESC, frames DESC LIMIT :limit")
                .map((rs, ctx) -> {
                    Observation o = observations.of(uuid(rs), DiscoAnomalyType.FRAME_OVERFLOW, rs.getString("bssid"),
                            null, time(rs, "minute"), time(rs, "first_seen"), time(rs, "last_seen"));
                    long frames = rs.getLong("frames");
                    o.frames += frames;
                    o.peak = max(o.peak, capped(frames));
                    o.frameTypes.addAll(strings(rs, "frame_types"));
                    return 1;
                })
                .list().size();

        warnAtLimit("too many different frames", rows);
    }

    private static Observation pairObservation(ResultSet rs, DiscoAnomalyType type, Observations observations)
            throws SQLException {
        String bssid = rs.getString("bssid");
        String transmitter = rs.getString("transmitter");
        String receiver = rs.getString("receiver");

        String client = transmitter.equals(bssid) ? receiver : receiver.equals(bssid) ? transmitter : receiver;
        if (Dot11.BROADCAST_MAC.equals(client)) {
            client = null;
        }

        Observation o = observations.of(uuid(rs), type, bssid, client,
                time(rs, "created_at"), time(rs, "first_seen"), time(rs, "last_seen"));
        long frames = rs.getLong("frames");
        o.frames += frames;
        if (transmitter.equals(bssid)) {
            o.framesFromBssid += frames;
        }
        o.reasonCodes.addAll(integers(rs, "reason_codes"));
        o.frameTypes.addAll(strings(rs, "frame_types"));

        return o;
    }

    private void apply(Handle handle,
                       Observations observations,
                       UUID organizationId,
                       UUID tenantId,
                       Map<UUID, Tap> taps,
                       Map<String, List<MonitoredSSID>> monitored) {
        Map<Key, Active> active = Maps.newHashMap();
        Map<Key, Active> changed = Maps.newLinkedHashMap();
        int created = 0;
        int skipped = 0;

        for (Observation o : observations.ordered(monitored.keySet())) {
            DateTime evaluatedUntil = o.minute.plusMinutes(1);

            Active anomaly = active.computeIfAbsent(o.key, key -> findActive(handle, key, o.firstSeen));
            if (anomaly == null) {
                if (created >= MAX_NEW_ANOMALIES_PER_RUN) {
                    skipped++;
                    continue;
                }

                anomaly = create(handle, o, evaluatedUntil);
                created++;
            } else if (anomaly.evaluatedUntil().isBefore(evaluatedUntil)) {
                update(handle, anomaly.id(), o, evaluatedUntil);
                anomaly = new Active(anomaly.id(), anomaly.uuid(), evaluatedUntil);
            } else {
                continue;
            }

            active.put(o.key, anomaly);
            changed.put(o.key, anomaly);
        }

        if (skipped > 0) {
            LOG.warn("Disconnection anomaly detection of tenant [{}/{}] recorded <{}> new anomalies in one run and " +
                    "left <{}> more unrecorded.", organizationId, tenantId, created, skipped);
        }

        Set<String> raised = Sets.newHashSet();
        for (Map.Entry<Key, Active> entry : changed.entrySet()) {
            Key key = entry.getKey();
            Tap tap = taps.get(key.tapUuid());

            if (tap == null) {
                continue;
            }

            for (MonitoredSSID network : monitored.getOrDefault(key.bssid(), List.of())) {
                if (!network.enabledDiscoMonitor()
                        || !raised.add(network.uuid() + "|" + tap.uuid() + "|" + key.bssid() + "|" + key.type())) {
                    continue;
                }

                try {
                    raiseAlert(network, tap, key, entry.getValue().uuid(), organizationId, tenantId);
                } catch (Exception e) {
                    LOG.error("Could not raise alert for disconnection anomaly [{}].", entry.getValue().uuid(), e);
                }
            }
        }
    }

    @Nullable
    private Active findActive(Handle handle, Key key, DateTime firstSeen) {
        return handle.createQuery("SELECT id, uuid, evaluated_until FROM dot11_disco_anomalies " +
                        "WHERE tap_uuid = :tap_uuid AND bssid = :bssid AND anomaly_type = :anomaly_type " +
                        "AND client IS NOT DISTINCT FROM :client AND last_seen >= :since " +
                        "ORDER BY last_seen DESC LIMIT 1")
                .bind("tap_uuid", key.tapUuid())
                .bind("bssid", key.bssid())
                .bind("anomaly_type", key.type().name())
                .bind("client", key.client())
                .bind("since", firstSeen.minusMinutes(MERGE_GAP_MINUTES))
                .map((rs, ctx) -> new Active(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("uuid")),
                        new DateTime(rs.getTimestamp("evaluated_until"))
                ))
                .findFirst()
                .orElse(null);
    }

    private Active create(Handle handle, Observation o, DateTime evaluatedUntil) {
        return handle.createQuery("INSERT INTO dot11_disco_anomalies(tap_uuid, anomaly_type, bssid, client, " +
                        "first_seen, last_seen, evaluated_until, frame_count, frames_from_bssid, peak, threshold, " +
                        "signal_strength, reference_signal_strength, reason_codes, frame_types, sample_clients, " +
                        "created_at, updated_at) VALUES(:tap_uuid, :anomaly_type, :bssid, :client, :first_seen, " +
                        ":last_seen, :evaluated_until, :frame_count, :frames_from_bssid, :peak, :threshold, " +
                        ":signal_strength, :reference_signal_strength, :reason_codes, :frame_types, " +
                        ":sample_clients, NOW(), NOW()) RETURNING id, uuid")
                .bind("tap_uuid", o.key.tapUuid())
                .bind("anomaly_type", o.key.type().name())
                .bind("bssid", o.key.bssid())
                .bind("client", o.key.client())
                .bind("first_seen", o.firstSeen)
                .bind("last_seen", o.lastSeen)
                .bind("evaluated_until", evaluatedUntil)
                .bind("frame_count", o.frames)
                .bind("frames_from_bssid", o.framesFromBssid)
                .bind("peak", o.peak)
                .bind("threshold", o.threshold)
                .bind("signal_strength", o.signal)
                .bind("reference_signal_strength", o.referenceSignal)
                .bind("reason_codes", o.reasonCodes.stream().limit(MAX_REASON_CODES).toArray(Integer[]::new))
                .bind("frame_types", o.frameTypes.toArray(new String[0]))
                .bind("sample_clients", o.clients.stream().limit(MAX_SAMPLE_CLIENTS).toArray(String[]::new))
                .map((rs, ctx) -> new Active(
                        rs.getLong("id"), UUID.fromString(rs.getString("uuid")), evaluatedUntil
                ))
                .one();
    }

    private void update(Handle handle, long id, Observation o, DateTime evaluatedUntil) {
        handle.createUpdate("UPDATE dot11_disco_anomalies SET first_seen = LEAST(first_seen, :first_seen), " +
                        "last_seen = GREATEST(last_seen, :last_seen), evaluated_until = :evaluated_until, " +
                        "frame_count = frame_count + :frame_count, " +
                        "frames_from_bssid = frames_from_bssid + :frames_from_bssid, " +
                        // The strengths that go with the largest difference. Every assignment sees the old row.
                        "signal_strength = CASE WHEN CAST(:peak AS integer) > COALESCE(peak, -1) " +
                        "THEN CAST(:signal_strength AS double precision) ELSE signal_strength END, " +
                        "reference_signal_strength = CASE WHEN CAST(:peak AS integer) > COALESCE(peak, -1) " +
                        "THEN CAST(:reference_signal_strength AS double precision) ELSE reference_signal_strength END, " +
                        "peak = GREATEST(peak, :peak), " +
                        "reason_codes = (ARRAY(SELECT DISTINCT x FROM unnest(reason_codes || :reason_codes) x " +
                        "ORDER BY x))[1 : " + MAX_REASON_CODES + "], " +
                        "frame_types = ARRAY(SELECT DISTINCT x FROM unnest(frame_types || :frame_types) x ORDER BY x), " +
                        "sample_clients = (ARRAY(SELECT DISTINCT x FROM unnest(sample_clients || :sample_clients) x " +
                        "ORDER BY x))[1 : " + MAX_SAMPLE_CLIENTS + "], updated_at = NOW() " +
                        "WHERE id = :id AND evaluated_until < :evaluated_until")
                .bind("id", id)
                .bind("first_seen", o.firstSeen)
                .bind("last_seen", o.lastSeen)
                .bind("evaluated_until", evaluatedUntil)
                .bind("frame_count", o.frames)
                .bind("frames_from_bssid", o.framesFromBssid)
                .bind("peak", o.peak)
                .bind("signal_strength", o.signal)
                .bind("reference_signal_strength", o.referenceSignal)
                .bind("reason_codes", o.reasonCodes.toArray(new Integer[0]))
                .bind("frame_types", o.frameTypes.toArray(new String[0]))
                .bind("sample_clients", o.clients.toArray(new String[0]))
                .execute();
    }

    private void raiseAlert(MonitoredSSID network, Tap tap, Key key, UUID anomalyUuid, UUID organizationId,
                            UUID tenantId) {
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("bssid", key.bssid());
        attributes.put("anomaly_type", key.type().name());
        attributes.put("anomaly_title", key.type().getTitle());
        attributes.put("anomaly_uuid", anomalyUuid.toString());
        attributes.put("client", key.client() == null ? "" : key.client());
        attributes.put("tap_id", tap.uuid().toString());
        attributes.put("tap_name", tap.name());

        nzyme.getDetectionAlertService().raiseAlert(
                organizationId,
                tenantId,
                network.uuid(),
                AlertOrigin.tap(tap.uuid()),
                DetectionType.DOT11_MONITOR_DISCO_ANOMALIES,
                Subsystem.DOT11,
                "Disconnection anomaly \"" + key.type().getTitle() + "\" for BSSID \"" + key.bssid() + "\" of " +
                        "monitored network \"" + network.ssid() + "\"",
                attributes,
                Set.of("bssid", "anomaly_type")
        );
    }

    private static UUID uuid(ResultSet rs) throws SQLException {
        return UUID.fromString(rs.getString("tap_uuid"));
    }

    private static DateTime time(ResultSet rs, String column) throws SQLException {
        return new DateTime(rs.getTimestamp(column));
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static List<Integer> integers(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((Integer[]) array.getArray());
    }

    private static int capped(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private static Integer max(@Nullable Integer current, int value) {
        return current == null ? value : Math.max(current, value);
    }

}
