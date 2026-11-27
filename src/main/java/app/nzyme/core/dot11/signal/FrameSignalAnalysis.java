package app.nzyme.core.dot11.signal;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class FrameSignalAnalysis {

    public static final int MAX_SOURCES = 2;
    public static final int MAX_CHARTS = 10;
    public static final int DEFAULT_CHARTS = 6;
    public static final int MAX_FRAME_RECORDS_PER_CHART = 1000;
    public static final int MAX_FRAME_RECORDS = 25000;
    public static final int MAX_FINDINGS_PER_PAGE = 100;
    public static final int MAX_TRANSMITTERS_PER_PAGE = 25;
    public static final int CLIENT_COMPARISON_MINUTES = 15;

    private static final int STATEMENT_TIMEOUT_MS = 30000;
    private static final String QUERY_CANCELED = "57014";

    private static final ObjectMapper OM = new ObjectMapper();

    public static final int REFERENCE_MINUTES = 5;
    public static final int MINIMUM_REFERENCE_FRAMES = 10;

    private final NzymeNode nzyme;

    public FrameSignalAnalysis(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public record Source(String address, boolean isAccessPoint, long frameCount, long comparedFrameCount,
                         long mismatchFrameCount, @Nullable Double difference, double largestDifference) { }

    public record Finding(UUID tapUuid, int frequency, String transmitter, long frameCount, double signal,
                          double signalMin, double signalMax, long comparedFrameCount,
                          @Nullable Double comparedWith, @Nullable Double difference, long mismatchFrameCount,
                          double largestDifference) { }

    public record Result(long sourceCount, List<Source> sources, long findingCount, List<Finding> findings,
                         boolean truncated, @Nullable DateTime firstFrame, @Nullable DateTime lastFrame) { }

    public record ReferenceBucket(DateTime bucket, double average, int min, int max, long samples) { }

    public record Reference(String source, List<ReferenceBucket> buckets) { }

    public record Frame(DateTime timestamp, String transmitter, @Nullable String receiver, String frameType,
                        @Nullable Integer reasonCode, @Nullable Integer newChannel, boolean isProtected,
                        long frameCount, double signal,
                        @Nullable Integer signalMin, @Nullable Integer signalMax,
                        @Nullable Map<Integer, Long> signalHistogram, @Nullable Double reference) { }

    public record Chart(UUID tapUuid, int frequency, long frameCount, boolean truncated, List<Reference> references,
                        List<Frame> frames) { }

    public record Charts(long sourceCount, List<Source> sources, List<Chart> charts, boolean truncated) { }

    public record ChartKey(UUID tapUuid, int frequency) { }

    public static class TooMuchDataException extends RuntimeException { }

    public record Transmitter(String address, boolean isAccessPoint, long frameCount, long receiverCount,
                              long comparisonFrameCount, DateTime lastSeen) { }

    public record Transmitters(long total, List<Transmitter> transmitters) { }

    private record FrameRecord(long id, UUID tapUuid, int frequency, DateTime createdAt, DateTime seenAt,
                               String transmitter, @Nullable String receiver, String frameType,
                               @Nullable Integer reasonCode, @Nullable Integer newChannel, boolean isProtected,
                               long frameCount, double signal, @Nullable Integer signalMin,
                               @Nullable Integer signalMax, @Nullable Map<Integer, Long> signalHistogram) {

        ChartKey key() {
            return new ChartKey(tapUuid, frequency);
        }

    }

    private record MinuteKey(UUID tapUuid, int frequency, long minute) { }

    private record SourceBase(String address, boolean isAccessPoint, long frameCount) { }

    private record References(Map<String, Map<MinuteKey, Double>> accessPointMinutes,
                              Map<String, Map<ChartKey, TreeMap<Long, Double>>> clientMinutes) { }

    private record Evaluation(List<Finding> findings, List<Source> sources) { }

    private static class Tally {
        long frames = 0;
        double signalSum = 0;
        double signalMin = Double.MAX_VALUE;
        double signalMax = -Double.MAX_VALUE;
        long comparedFrames = 0;
        double comparedSignalSum = 0;
        double comparisonSum = 0;
        long mismatchFrames = 0;
        double largestDifference = 0;

        void add(double signal, long frameCount, @Nullable Double comparedWith, int threshold) {
            frames += frameCount;
            signalSum += signal * frameCount;
            signalMin = Math.min(signalMin, signal);
            signalMax = Math.max(signalMax, signal);

            if (comparedWith != null) {
                double difference = signal - comparedWith;

                comparedFrames += frameCount;
                comparedSignalSum += signal * frameCount;
                comparisonSum += comparedWith * frameCount;

                if (Math.abs(difference) >= threshold) {
                    mismatchFrames += frameCount;
                }

                if (Math.abs(difference) > Math.abs(largestDifference)) {
                    largestDifference = difference;
                }
            }
        }

        void add(Tally other) {
            frames += other.frames;
            signalSum += other.signalSum;
            signalMin = Math.min(signalMin, other.signalMin);
            signalMax = Math.max(signalMax, other.signalMax);
            comparedFrames += other.comparedFrames;
            comparedSignalSum += other.comparedSignalSum;
            comparisonSum += other.comparisonSum;
            mismatchFrames += other.mismatchFrames;

            if (Math.abs(other.largestDifference) > Math.abs(largestDifference)) {
                largestDifference = other.largestDifference;
            }
        }

        @Nullable
        Double comparedWith() {
            return comparedFrames == 0 ? null : comparisonSum / comparedFrames;
        }

        @Nullable
        Double difference() {
            return comparedFrames == 0 ? null : (comparedSignalSum - comparisonSum) / comparedFrames;
        }
    }

    private static final Comparator<Finding> BY_IMPORTANCE = Comparator
            .comparingLong((Finding f) -> -f.mismatchFrameCount())
            .thenComparing(f -> f.comparedFrameCount() == 0)
            .thenComparingDouble(f -> -Math.abs(f.largestDifference()))
            .thenComparingLong(f -> -f.frameCount())
            .thenComparing(f -> f.tapUuid().toString())
            .thenComparingInt(Finding::frequency)
            .thenComparing(Finding::transmitter);

    public Result analyze(FrameSource source, TimeRange timeRange, Filters filters, List<UUID> taps,
                          int threshold, int limit, int offset) {
        if (taps.isEmpty()) {
            return new Result(0, List.of(), 0, List.of(), false, null, null);
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, source.filters());

        return limited(handle -> {
            long sourceCount = sourceCount(handle, source, timeRange, taps, filterFragment);
            if (sourceCount == 0 || sourceCount > MAX_SOURCES) {
                return new Result(sourceCount, List.of(), 0, List.of(), false, null, null);
            }

            List<SourceBase> sources = sources(handle, source, timeRange, taps, filterFragment);
            List<FrameRecord> records = frameRecords(handle, source, timeRange, taps, filterFragment, null);
            boolean truncated = records.size() > MAX_FRAME_RECORDS;
            if (truncated) {
                records = records.subList(0, MAX_FRAME_RECORDS);
            }

            References references = references(handle, sources, records, taps);
            Evaluation evaluation = evaluate(sources, records, references, threshold);

            DateTime first = null;
            DateTime last = null;
            for (FrameRecord record : records) {
                first = first == null || record.seenAt().isBefore(first) ? record.seenAt() : first;
                last = last == null || record.seenAt().isAfter(last) ? record.seenAt() : last;
            }

            List<Finding> findings = evaluation.findings();
            int from = Math.min(Math.max(offset, 0), findings.size());
            int to = Math.min(from + Math.min(Math.max(limit, 1), MAX_FINDINGS_PER_PAGE), findings.size());

            return new Result(sourceCount, evaluation.sources(), findings.size(),
                    Lists.newArrayList(findings.subList(from, to)), truncated, first, last);
        });
    }

    public Charts charts(FrameSource source, TimeRange timeRange, Bucketing.BucketingConfiguration bucketing,
                         Filters filters, List<UUID> taps, int threshold, @Nullable List<ChartKey> selection) {
        if (taps.isEmpty() || (selection != null && selection.isEmpty())) {
            return new Charts(0, List.of(), List.of(), false);
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, source.filters());

        return limited(handle -> {
            long sourceCount = sourceCount(handle, source, timeRange, taps, filterFragment);
            if (sourceCount == 0 || sourceCount > MAX_SOURCES) {
                return new Charts(sourceCount, List.of(), List.of(), false);
            }

            List<ChartKey> selected = selection == null
                    ? null : selection.subList(0, Math.min(selection.size(), MAX_CHARTS));

            List<SourceBase> sources = sources(handle, source, timeRange, taps, filterFragment);
            List<FrameRecord> records = frameRecords(handle, source, timeRange, taps, filterFragment, selected);
            boolean recordsTruncated = records.size() > MAX_FRAME_RECORDS;
            if (recordsTruncated) {
                records = records.subList(0, MAX_FRAME_RECORDS);
            }

            References references = references(handle, sources, records, taps);
            Evaluation evaluation = evaluate(sources, records, references, threshold);

            List<ChartKey> keys = Lists.newArrayList();
            if (selected == null) {
                for (Finding finding : evaluation.findings()) {
                    ChartKey key = new ChartKey(finding.tapUuid(), finding.frequency());
                    if (!keys.contains(key) && keys.size() < DEFAULT_CHARTS) {
                        keys.add(key);
                    }
                }
            } else {
                for (ChartKey key : selected) {
                    if (!keys.contains(key)) {
                        keys.add(key);
                    }
                }
            }

            Map<ChartKey, List<FrameRecord>> byChart = Maps.newHashMap();
            for (FrameRecord record : records) {
                byChart.computeIfAbsent(record.key(), k -> Lists.newArrayList()).add(record);
            }

            keys.removeIf(key -> !byChart.containsKey(key));

            Map<String, Map<ChartKey, List<ReferenceBucket>>> lines = Maps.newHashMap();
            for (SourceBase transmitter : sources) {
                lines.put(transmitter.address(), keys.isEmpty() ? Map.of() : transmitter.isAccessPoint()
                        ? accessPointLines(handle, transmitter.address(), keys, timeRange, bucketing)
                        : clientLines(handle, transmitter.address(), keys, timeRange, bucketing));
            }

            List<Chart> charts = Lists.newArrayList();
            for (ChartKey key : keys) {
                List<FrameRecord> ofChart = byChart.get(key);

                ofChart.sort(Comparator.comparing((FrameRecord r) -> r.seenAt().getMillis())
                        .thenComparingLong(FrameRecord::id));

                long frameCount = ofChart.stream().mapToLong(FrameRecord::frameCount).sum();
                boolean truncated = ofChart.size() > MAX_FRAME_RECORDS_PER_CHART;

                List<Frame> frames = Lists.newArrayList();
                for (FrameRecord r : truncated ? ofChart.subList(0, MAX_FRAME_RECORDS_PER_CHART) : ofChart) {
                    frames.add(new Frame(r.seenAt(), r.transmitter(), r.receiver(), r.frameType(), r.reasonCode(),
                            r.newChannel(), r.isProtected(), r.frameCount(), r.signal(), r.signalMin(), r.signalMax(),
                            r.signalHistogram(), comparedWith(r, sources, references)));
                }

                List<Reference> chartReferences = Lists.newArrayList();
                for (SourceBase transmitter : sources) {
                    chartReferences.add(new Reference(transmitter.address(),
                            lines.get(transmitter.address()).getOrDefault(key, List.of())));
                }

                charts.add(new Chart(key.tapUuid(), key.frequency(), frameCount, truncated, chartReferences, frames));
            }

            return new Charts(sourceCount, evaluation.sources(), charts, recordsTruncated);
        });
    }

    public Transmitters transmitters(FrameSource source, TimeRange timeRange, List<UUID> taps, int limit,
                                     int offset) {
        if (taps.isEmpty()) {
            return new Transmitters(0, List.of());
        }

        return limited(handle -> {
            long total = handle.createQuery("SELECT COUNT(DISTINCT d.transmitter) " + source.framesSql())
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bindList("taps", taps)
                    .mapTo(Long.class)
                    .one();

            List<Object[]> rows = handle.createQuery("SELECT d.transmitter, SUM(d.frame_count) AS frames, " +
                            "COUNT(DISTINCT d.receiver) AS receivers, " +
                            "MAX(COALESCE(d.last_seen, d.created_at)) AS last_seen " + source.framesSql() +
                            "GROUP BY d.transmitter ORDER BY frames DESC, d.transmitter LIMIT :limit OFFSET :offset")
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bindList("taps", taps)
                    .bind("limit", limit <= 0 ? 10 : Math.min(limit, MAX_TRANSMITTERS_PER_PAGE))
                    .bind("offset", Math.max(offset, 0))
                    .map((rs, ctx) -> new Object[]{rs.getString("transmitter"), rs.getLong("frames"),
                            rs.getLong("receivers"), new DateTime(rs.getTimestamp("last_seen"))})
                    .list();

            if (rows.isEmpty()) {
                return new Transmitters(total, List.of());
            }

            List<String> addresses = rows.stream().map(row -> (String) row[0]).toList();

            Set<String> accessPoints = source.accessPointsOnly() ? Sets.newHashSet(addresses)
                    : Sets.newHashSet(handle.createQuery("SELECT DISTINCT bssid FROM dot11_bssids " +
                            "WHERE bssid IN (<addresses>) AND tap_uuid IN (<taps>) AND created_at >= :from " +
                            "AND created_at <= :to")
                    .bindList("addresses", addresses)
                    .bindList("taps", taps)
                    .bind("from", timeRange.from().minusMinutes(REFERENCE_MINUTES + 1))
                    .bind("to", timeRange.to())
                    .mapTo(String.class)
                    .list());

            Map<String, Long> comparisonFrames =
                    comparisonFrames(handle, source, addresses, accessPoints, timeRange, taps);

            List<Transmitter> transmitters = Lists.newArrayList();
            for (Object[] row : rows) {
                String address = (String) row[0];

                transmitters.add(new Transmitter(address, accessPoints.contains(address), (long) row[1],
                        (long) row[2], comparisonFrames.getOrDefault(address, 0L), (DateTime) row[3]));
            }

            return new Transmitters(total, transmitters);
        });
    }

    private Evaluation evaluate(List<SourceBase> sources, List<FrameRecord> records, References references,
                                int threshold) {
        Map<String, Map<ChartKey, Tally>> tallies = new LinkedHashMap<>();
        for (SourceBase source : sources) {
            tallies.put(source.address(), Maps.newHashMap());
        }

        for (FrameRecord record : records) {
            Map<ChartKey, Tally> ofSource = tallies.get(record.transmitter());
            if (ofSource == null) {
                continue;
            }

            Tally tally = ofSource.computeIfAbsent(record.key(), k -> new Tally());
            Double comparedWith = comparedWith(record, sources, references);

            if (record.signalHistogram() != null && !record.signalHistogram().isEmpty()) {
                for (Map.Entry<Integer, Long> mark : record.signalHistogram().entrySet()) {
                    tally.add(mark.getKey(), mark.getValue(), comparedWith, threshold);
                }
            } else {
                tally.add(record.signal(), record.frameCount(), comparedWith, threshold);
            }
        }

        List<Finding> findings = Lists.newArrayList();
        List<Source> evaluated = Lists.newArrayList();
        for (SourceBase source : sources) {
            Tally total = new Tally();

            for (Map.Entry<ChartKey, Tally> entry : tallies.get(source.address()).entrySet()) {
                Tally tally = entry.getValue();
                total.add(tally);

                findings.add(new Finding(entry.getKey().tapUuid(), entry.getKey().frequency(), source.address(),
                        tally.frames, tally.signalSum / tally.frames, tally.signalMin, tally.signalMax,
                        tally.comparedFrames, tally.comparedWith(), tally.difference(), tally.mismatchFrames,
                        tally.largestDifference));
            }

            evaluated.add(new Source(source.address(), source.isAccessPoint(), source.frameCount(),
                    total.comparedFrames, total.mismatchFrames, total.difference(), total.largestDifference));
        }

        findings.sort(BY_IMPORTANCE);

        return new Evaluation(findings, evaluated);
    }

    @Nullable
    private Double comparedWith(FrameRecord record, List<SourceBase> sources, References references) {
        for (SourceBase source : sources) {
            if (!source.address().equals(record.transmitter())) {
                continue;
            }

            if (source.isAccessPoint()) {
                long minute = record.createdAt().getMillis() / 60000 * 60000;

                return references.accessPointMinutes().getOrDefault(source.address(), Map.of())
                        .get(new MinuteKey(record.tapUuid(), record.frequency(), minute));
            }

            TreeMap<Long, Double> minutes = references.clientMinutes().getOrDefault(source.address(), Map.of())
                    .get(record.key());
            if (minutes == null) {
                return null;
            }

            long at = record.seenAt().getMillis();
            long tolerance = CLIENT_COMPARISON_MINUTES * 60000L;

            // The minute the frame is in, or else the nearer one of the minutes before and after it.
            Map.Entry<Long, Double> before = minutes.floorEntry(at);
            Map.Entry<Long, Double> after = minutes.ceilingEntry(at);
            long distanceBefore = before == null ? Long.MAX_VALUE : Math.max(0, at - (before.getKey() + 60000L));
            long distanceAfter = after == null ? Long.MAX_VALUE : after.getKey() - at;

            if (Math.min(distanceBefore, distanceAfter) > tolerance) {
                return null;
            }

            return distanceBefore <= distanceAfter ? before.getValue() : after.getValue();
        }

        return null;
    }

    private References references(Handle handle, List<SourceBase> sources, List<FrameRecord> records,
                                  List<UUID> taps) {
        Map<String, Map<MinuteKey, Double>> accessPointMinutes = Maps.newHashMap();
        Map<String, Map<ChartKey, TreeMap<Long, Double>>> clientMinutes = Maps.newHashMap();

        for (SourceBase source : sources) {
            // An access point is judged by the minute a frame was reported in, like the detection does. A client
            // by the time the frame was seen.
            Set<MinuteKey> minutes = Sets.newHashSet();
            for (FrameRecord record : records) {
                if (record.transmitter().equals(source.address())) {
                    DateTime at = source.isAccessPoint() ? record.createdAt() : record.seenAt();
                    minutes.add(new MinuteKey(record.tapUuid(), record.frequency(), at.getMillis() / 60000 * 60000));
                }
            }

            if (minutes.isEmpty()) {
                continue;
            }

            if (source.isAccessPoint()) {
                accessPointMinutes.put(source.address(), accessPointMinutes(handle, source.address(), minutes, taps));
            } else {
                clientMinutes.put(source.address(), clientMinutes(handle, source.address(), minutes, taps));
            }
        }

        return new References(accessPointMinutes, clientMinutes);
    }

    private <T> T limited(HandleCallback<T, RuntimeException> callback) {
        try {
            return nzyme.getDatabase().inTransaction(handle -> {
                handle.execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);
                return callback.withHandle(handle);
            });
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState())) {
                throw new TooMuchDataException();
            }

            throw e;
        }
    }

    /*
     * Frames that announce a channel switch are taken out of the beacons: anyone can send them in the name of the
     * access point. A tap does not count every announcing frame as a frame of a network, so what is left of a
     * signal strength never goes below zero.
     *
     * The scope names the columns it restricts as {tap}, {bssid} and {frequency}.
     */
    public static String beaconsCte(String scope) {
        return "beacons AS (SELECT x.tap_uuid, x.bssid, x.frequency, x.created_at, x.signal_strength, " +
                "SUM(x.frames) AS frames FROM (" +
                "SELECT b.tap_uuid, b.bssid, h.frequency, b.created_at, h.signal_strength, " +
                "h.frame_count::bigint AS frames FROM dot11_bssids b JOIN dot11_ssids s ON s.bssid_id = b.id " +
                "JOIN dot11_channel_histograms h ON h.ssid_id = s.id " +
                "WHERE b.created_at >= :reference_from AND b.created_at < :reference_to AND " +
                scope.replace("{tap}", "b.tap_uuid").replace("{bssid}", "b.bssid").replace("{frequency}", "h.frequency") +
                " UNION ALL " +
                "SELECT b.tap_uuid, b.bssid, f.key::int, b.created_at, v.key::int, v.value::bigint " +
                "FROM dot11_bssids b CROSS JOIN LATERAL jsonb_each(b.hidden_signal_histogram) f " +
                "CROSS JOIN LATERAL jsonb_each_text(f.value) v " +
                "WHERE b.created_at >= :reference_from AND b.created_at < :reference_to " +
                "AND b.hidden_signal_histogram IS NOT NULL AND " +
                scope.replace("{tap}", "b.tap_uuid").replace("{bssid}", "b.bssid").replace("{frequency}", "f.key::int") +
                " UNION ALL " +
                "SELECT a.tap_uuid, a.bssid, f.key::int, a.created_at, v.key::int, -(v.value::bigint) " +
                "FROM dot11_channel_switches a CROSS JOIN LATERAL jsonb_each(a.signal_histograms) f " +
                "CROSS JOIN LATERAL jsonb_each_text(f.value) v " +
                "WHERE a.created_at >= :reference_from AND a.created_at < :reference_to " +
                "AND a.frame_type IN ('BEACON', 'PROBE_RESPONSE') AND " +
                scope.replace("{tap}", "a.tap_uuid").replace("{bssid}", "a.bssid").replace("{frequency}", "f.key::int") +
                ") x GROUP BY x.tap_uuid, x.bssid, x.frequency, x.created_at, x.signal_strength " +
                "HAVING SUM(x.frames) > 0) ";
    }

    public static final String BEACON_MINUTES = "beacon_minutes AS (SELECT tap_uuid, bssid, frequency, " +
            "date_trunc('minute', created_at) AS minute, SUM(signal_strength * frames) AS signal_sum, " +
            "SUM(frames) AS frames FROM beacons GROUP BY tap_uuid, bssid, frequency, " +
            "date_trunc('minute', created_at)) ";

    private static Query bindMinutes(Query query, Set<MinuteKey> minutes) {
        return query
                .bind("minute_taps", minutes.stream().map(m -> m.tapUuid().toString()).toArray(String[]::new))
                .bind("minute_frequencies", minutes.stream().map(m -> String.valueOf(m.frequency())).toArray(String[]::new))
                .bind("minute_times", minutes.stream()
                        .map(m -> new DateTime(m.minute(), DateTimeZone.UTC).toString()).toArray(String[]::new));
    }

    private static final String FRAME_MINUTES = "frame_minutes AS (SELECT * FROM unnest(" +
            "CAST(:minute_taps AS uuid[]), CAST(:minute_frequencies AS integer[]), " +
            "CAST(:minute_times AS timestamptz[])) AS m(tap_uuid, frequency, minute)) ";

    private static Query bind(Query query, TimeRange timeRange, List<UUID> taps, FilterSqlFragment filterFragment,
                              @Nullable List<ChartKey> selection) {
        query.bind("tr_from", timeRange.from())
                .bind("tr_to", timeRange.to())
                .bindList("taps", taps)
                .bindMap(filterFragment.bindings());

        if (selection != null) {
            for (int i = 0; i < selection.size(); i++) {
                query.bind("selected_tap_" + i, selection.get(i).tapUuid())
                        .bind("selected_frequency_" + i, selection.get(i).frequency());
            }
        }

        return query;
    }

    private static String selectionSql(@Nullable List<ChartKey> selection) {
        if (selection == null) {
            return "";
        }

        List<String> conditions = Lists.newArrayList();
        for (int i = 0; i < selection.size(); i++) {
            conditions.add("(d.tap_uuid = :selected_tap_" + i + " AND d.frequency = :selected_frequency_" + i + ")");
        }

        return " AND (" + String.join(" OR ", conditions) + ") ";
    }

    private long sourceCount(Handle handle, FrameSource source, TimeRange timeRange, List<UUID> taps,
                             FilterSqlFragment filterFragment) {
        return bind(handle.createQuery("SELECT COUNT(*) FROM (SELECT DISTINCT d.transmitter " + source.framesSql() +
                filterFragment.whereSql() + " LIMIT " + (MAX_SOURCES + 1) + ") t"), timeRange, taps, filterFragment, null)
                .mapTo(Long.class)
                .one();
    }

    private List<SourceBase> sources(Handle handle, FrameSource source, TimeRange timeRange, List<UUID> taps,
                                     FilterSqlFragment filterFragment) {
        List<SourceBase> sources = Lists.newArrayList();
        bind(handle.createQuery("SELECT d.transmitter, SUM(d.frame_count) AS frames " + source.framesSql() +
                        filterFragment.whereSql() + " GROUP BY d.transmitter ORDER BY frames DESC, d.transmitter " +
                        "LIMIT " + MAX_SOURCES),
                timeRange, taps, filterFragment, null)
                .map((rs, ctx) -> new Object[]{rs.getString("transmitter"), rs.getLong("frames")})
                .forEach(row -> sources.add(new SourceBase(
                        (String) row[0],
                        source.accessPointsOnly() || isAccessPoint(handle, (String) row[0], taps, timeRange),
                        (long) row[1]
                )));

        return sources;
    }

    private boolean isAccessPoint(Handle handle, String address, List<UUID> taps, TimeRange timeRange) {
        return handle.createQuery("SELECT EXISTS (SELECT 1 FROM dot11_bssids WHERE bssid = :bssid " +
                        "AND tap_uuid IN (<taps>) AND created_at >= :from AND created_at <= :to)")
                .bind("bssid", address)
                .bindList("taps", taps)
                .bind("from", timeRange.from().minusMinutes(REFERENCE_MINUTES + 1))
                .bind("to", timeRange.to())
                .mapTo(Boolean.class)
                .one();
    }

    private List<FrameRecord> frameRecords(Handle handle, FrameSource source, TimeRange timeRange, List<UUID> taps,
                                           FilterSqlFragment filterFragment, @Nullable List<ChartKey> selection) {
        return bind(handle.createQuery("SELECT d.id, d.tap_uuid, d.frequency, d.created_at, " +
                        "COALESCE(d.first_seen, d.created_at) AS seen_at, d.transmitter, d.receiver, " +
                        source.frameTypeColumn() + " AS frame_type, " + source.newChannelColumn() + " AS new_channel, " +
                        "d.reason_code, d.is_protected, d.frame_count, d.signal_strength_average, " +
                        "d.signal_strength_min, d.signal_strength_max, d.signal_histogram::text AS signal_histogram " +
                        source.framesSql() + filterFragment.whereSql() + selectionSql(selection) +
                        "ORDER BY d.created_at DESC, d.id DESC LIMIT :limit"),
                timeRange, taps, filterFragment, selection)
                .bind("limit", MAX_FRAME_RECORDS + 1)
                .map((rs, ctx) -> {
                    String histogram = rs.getString("signal_histogram");

                    return new FrameRecord(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("tap_uuid")),
                            rs.getInt("frequency"),
                            new DateTime(rs.getTimestamp("created_at")),
                            new DateTime(rs.getTimestamp("seen_at")),
                            rs.getString("transmitter"),
                            rs.getString("receiver"),
                            rs.getString("frame_type"),
                            rs.getObject("reason_code", Integer.class),
                            rs.getObject("new_channel", Integer.class),
                            rs.getBoolean("is_protected"),
                            rs.getLong("frame_count"),
                            rs.getDouble("signal_strength_average"),
                            rs.getObject("signal_strength_min", Integer.class),
                            rs.getObject("signal_strength_max", Integer.class),
                            histogram == null ? null : OM.readValue(histogram, new TypeReference<Map<Integer, Long>>() {})
                    );
                })
                .list();
    }

    private Map<MinuteKey, Double> accessPointMinutes(Handle handle, String bssid, Set<MinuteKey> minutes,
                                                      List<UUID> taps) {
        long first = minutes.stream().mapToLong(MinuteKey::minute).min().orElseThrow();
        long last = minutes.stream().mapToLong(MinuteKey::minute).max().orElseThrow();

        Map<MinuteKey, Double> references = Maps.newHashMap();

        bindMinutes(handle.createQuery("WITH " + FRAME_MINUTES + ", " +
                        beaconsCte("{bssid} = :bssid AND {tap} IN (<taps>) AND ({tap}, {frequency}) IN (" +
                                "SELECT tap_uuid, frequency FROM frame_minutes)") + ", " + BEACON_MINUTES +
                        "SELECT m.tap_uuid, m.frequency, m.minute, " +
                        "SUM(r.signal_sum)::float / SUM(r.frames) AS reference FROM frame_minutes m " +
                        "CROSS JOIN generate_series(0, :reference_minutes) g " +
                        "JOIN beacon_minutes r ON r.tap_uuid = m.tap_uuid AND r.frequency = m.frequency " +
                        "AND r.minute = m.minute - make_interval(mins => g) " +
                        "GROUP BY m.tap_uuid, m.frequency, m.minute " +
                        "HAVING SUM(r.frames) >= :minimum_reference_frames"), minutes)
                .bind("bssid", bssid)
                .bindList("taps", taps)
                .bind("reference_from", new DateTime(first).minusMinutes(REFERENCE_MINUTES))
                .bind("reference_to", new DateTime(last).plusMinutes(1))
                .bind("reference_minutes", REFERENCE_MINUTES)
                .bind("minimum_reference_frames", MINIMUM_REFERENCE_FRAMES)
                .map((rs, ctx) -> Map.entry(
                        new MinuteKey(UUID.fromString(rs.getString("tap_uuid")), rs.getInt("frequency"),
                                rs.getTimestamp("minute").getTime()),
                        rs.getDouble("reference")))
                .forEach(entry -> references.put(entry.getKey(), entry.getValue()));

        return references;
    }

    // Stored per frequency as [average, minimum, maximum, frames].
    private static final String BY_FREQUENCY = "e.key::int AS frequency, (e.value ->> 0)::float AS average, " +
            "(e.value ->> 1)::int AS signal_min, (e.value ->> 2)::int AS signal_max, " +
            "(e.value ->> 3)::bigint AS frames ";

    private static final String CLIENT_FRAMES = "SELECT f.tap_uuid, f.created_at AS seen_at, f.frequency, " +
            "f.signal_strength_average AS average, f.signal_strength_min AS signal_min, " +
            "f.signal_strength_max AS signal_max, f.frame_count AS frames " +
            "FROM dot11_connection_frames f WHERE f.tap_uuid IN (<taps>) AND f.created_at >= :reference_from " +
            "AND f.created_at <= :reference_to AND f.transmitter = :mac AND f.signal_strength_average IS NOT NULL " +
            "UNION ALL " +
            "SELECT b.tap_uuid, b.created_at AS seen_at, " + BY_FREQUENCY +
            "FROM dot11_bssid_clients c JOIN dot11_bssids b ON b.id = c.bssid_id " +
            "CROSS JOIN LATERAL jsonb_each(c.signal_by_frequency) e " +
            "WHERE c.client_mac = :mac AND b.tap_uuid IN (<taps>) AND b.created_at >= :reference_from " +
            "AND b.created_at <= :reference_to AND jsonb_typeof(e.value) = 'array' " +
            "UNION ALL " +
            "SELECT p.tap_uuid, p.created_at AS seen_at, " + BY_FREQUENCY +
            "FROM dot11_clients p CROSS JOIN LATERAL jsonb_each(p.signal_by_frequency) e " +
            "WHERE p.tap_uuid IN (<taps>) AND p.client_mac = :mac AND p.created_at >= :reference_from " +
            "AND p.created_at <= :reference_to AND jsonb_typeof(e.value) = 'array'";

    private Map<ChartKey, TreeMap<Long, Double>> clientMinutes(Handle handle, String mac, Set<MinuteKey> minutes,
                                                               List<UUID> taps) {
        long first = minutes.stream().mapToLong(MinuteKey::minute).min().orElseThrow();
        long last = minutes.stream().mapToLong(MinuteKey::minute).max().orElseThrow();
        int reach = CLIENT_COMPARISON_MINUTES + 1;

        Map<ChartKey, TreeMap<Long, Double>> references = Maps.newHashMap();

        bindMinutes(handle.createQuery("WITH " + FRAME_MINUTES + ", " +
                        "wanted AS (SELECT DISTINCT m.tap_uuid, m.frequency, " +
                        "m.minute + make_interval(mins => g) AS minute FROM frame_minutes m " +
                        "CROSS JOIN generate_series(-:reach, :reach) g) " +
                        "SELECT r.tap_uuid, r.frequency, date_trunc('minute', r.seen_at) AS minute, " +
                        "SUM(r.average * r.frames)::float / SUM(r.frames) AS average " +
                        "FROM (" + CLIENT_FRAMES + ") r " +
                        "WHERE (r.tap_uuid, r.frequency, date_trunc('minute', r.seen_at)) IN (" +
                        "SELECT tap_uuid, frequency, minute FROM wanted) " +
                        "GROUP BY r.tap_uuid, r.frequency, date_trunc('minute', r.seen_at)"), minutes)
                .bind("mac", mac)
                .bindList("taps", taps)
                .bind("reach", reach)
                .bind("reference_from", new DateTime(first).minusMinutes(reach))
                .bind("reference_to", new DateTime(last).plusMinutes(reach + 1))
                .map((rs, ctx) -> Map.entry(
                        new ChartKey(UUID.fromString(rs.getString("tap_uuid")), rs.getInt("frequency")),
                        Map.entry(rs.getTimestamp("minute").getTime(), rs.getDouble("average"))))
                .forEach(entry -> references.computeIfAbsent(entry.getKey(), k -> new TreeMap<>())
                        .put(entry.getValue().getKey(), entry.getValue().getValue()));

        return references;
    }

    private static Query bindCharts(Query query, List<ChartKey> keys) {
        return query
                .bind("chart_taps", keys.stream().map(k -> k.tapUuid().toString()).toArray(String[]::new))
                .bind("chart_frequencies", keys.stream().map(k -> String.valueOf(k.frequency())).toArray(String[]::new));
    }

    private static final String CHARTS = "charts AS (SELECT * FROM unnest(CAST(:chart_taps AS uuid[]), " +
            "CAST(:chart_frequencies AS integer[])) AS k(tap_uuid, frequency)) ";

    private static Map<ChartKey, List<ReferenceBucket>> lines(Query query) {
        Map<ChartKey, List<ReferenceBucket>> lines = Maps.newHashMap();

        query.map((rs, ctx) -> Map.entry(
                        new ChartKey(UUID.fromString(rs.getString("tap_uuid")), rs.getInt("frequency")),
                        new ReferenceBucket(new DateTime(rs.getTimestamp("bucket")), rs.getDouble("average"),
                                rs.getInt("signal_min"), rs.getInt("signal_max"), rs.getLong("samples"))))
                .forEach(entry -> lines.computeIfAbsent(entry.getKey(), k -> Lists.newArrayList())
                        .add(entry.getValue()));

        return lines;
    }

    private Map<ChartKey, List<ReferenceBucket>> accessPointLines(Handle handle, String bssid, List<ChartKey> keys,
                                                                 TimeRange timeRange,
                                                                 Bucketing.BucketingConfiguration bucketing) {
        return lines(bindCharts(handle.createQuery("WITH " + CHARTS + ", " +
                        beaconsCte("{bssid} = :bssid AND {tap} = ANY(CAST(:chart_taps AS uuid[])) " +
                                "AND ({tap}, {frequency}) IN (SELECT tap_uuid, frequency FROM charts)") +
                        "SELECT tap_uuid, frequency, date_trunc(:date_trunc, created_at) AS bucket, " +
                        "SUM(signal_strength * frames)::float / SUM(frames) AS average, " +
                        "MIN(signal_strength) AS signal_min, MAX(signal_strength) AS signal_max, " +
                        "SUM(frames) AS samples FROM beacons " +
                        "GROUP BY tap_uuid, frequency, bucket ORDER BY tap_uuid, frequency, bucket"), keys)
                .bind("date_trunc", bucketing.type().getDateTruncName())
                .bind("bssid", bssid)
                .bind("reference_from", timeRange.from())
                .bind("reference_to", timeRange.to().plusMillis(1)));
    }

    private Map<ChartKey, List<ReferenceBucket>> clientLines(Handle handle, String mac, List<ChartKey> keys,
                                                            TimeRange timeRange,
                                                            Bucketing.BucketingConfiguration bucketing) {
        return lines(bindCharts(handle.createQuery("WITH " + CHARTS +
                        "SELECT r.tap_uuid, r.frequency, date_trunc(:date_trunc, r.seen_at) AS bucket, " +
                        "SUM(r.average * r.frames)::float / SUM(r.frames) AS average, " +
                        "MIN(r.signal_min) AS signal_min, MAX(r.signal_max) AS signal_max, " +
                        "SUM(r.frames) AS samples FROM (" + CLIENT_FRAMES + ") r " +
                        "WHERE (r.tap_uuid, r.frequency) IN (SELECT tap_uuid, frequency FROM charts) " +
                        "GROUP BY r.tap_uuid, r.frequency, bucket ORDER BY r.tap_uuid, r.frequency, bucket"), keys)
                .bind("date_trunc", bucketing.type().getDateTruncName())
                .bind("mac", mac)
                .bindList("taps", keys.stream().map(ChartKey::tapUuid).distinct().toList())
                .bind("reference_from", timeRange.from())
                .bind("reference_to", timeRange.to()));
    }

    private Map<String, Long> comparisonFrames(Handle handle, FrameSource source, List<String> addresses,
                                               Set<String> accessPoints, TimeRange timeRange, List<UUID> taps) {
        String combinations = "WITH combinations AS (SELECT DISTINCT d.transmitter, d.tap_uuid, d.frequency " +
                source.framesSql() + "AND d.transmitter IN (<addresses>)) ";
        String onCombination = "IN (SELECT transmitter, tap_uuid, frequency FROM combinations)";

        Map<String, Long> frames = Maps.newHashMap();

        List<String> clients = addresses.stream().filter(address -> !accessPoints.contains(address)).toList();
        List<String> ofAccessPoints = addresses.stream().filter(accessPoints::contains).toList();

        if (!ofAccessPoints.isEmpty()) {
            handle.createQuery(combinations + ", " +
                            beaconsCte("{bssid} IN (<addresses>) AND {tap} IN (<taps>) " +
                                    "AND ({bssid}, {tap}, {frequency}) " + onCombination) +
                            "SELECT bssid AS address, SUM(frames) AS frames FROM beacons GROUP BY bssid")
                    .bindList("addresses", ofAccessPoints)
                    .bindList("taps", taps)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .bind("reference_from", timeRange.from())
                    .bind("reference_to", timeRange.to().plusMillis(1))
                    .map((rs, ctx) -> Map.entry(rs.getString("address"), rs.getLong("frames")))
                    .forEach(entry -> frames.put(entry.getKey(), entry.getValue()));
        }

        if (!clients.isEmpty()) {
            handle.createQuery(combinations + "SELECT r.address, SUM(r.frames) AS frames FROM (" +
                            "SELECT f.transmitter AS address, f.tap_uuid, f.frequency, f.frame_count AS frames " +
                            "FROM dot11_connection_frames f WHERE f.tap_uuid IN (<taps>) " +
                            "AND f.created_at >= :tr_from AND f.created_at <= :tr_to " +
                            "AND f.transmitter IN (<addresses>) AND f.signal_strength_average IS NOT NULL " +
                            "UNION ALL " +
                            "SELECT c.client_mac, b.tap_uuid, e.key::int, (e.value ->> 3)::bigint " +
                            "FROM dot11_bssid_clients c JOIN dot11_bssids b ON b.id = c.bssid_id " +
                            "CROSS JOIN LATERAL jsonb_each(c.signal_by_frequency) e " +
                            "WHERE c.client_mac IN (<addresses>) AND b.tap_uuid IN (<taps>) " +
                            "AND b.created_at >= :tr_from AND b.created_at <= :tr_to " +
                            "AND jsonb_typeof(e.value) = 'array' " +
                            "UNION ALL " +
                            "SELECT p.client_mac, p.tap_uuid, e.key::int, (e.value ->> 3)::bigint " +
                            "FROM dot11_clients p CROSS JOIN LATERAL jsonb_each(p.signal_by_frequency) e " +
                            "WHERE p.tap_uuid IN (<taps>) AND p.client_mac IN (<addresses>) " +
                            "AND p.created_at >= :tr_from AND p.created_at <= :tr_to " +
                            "AND jsonb_typeof(e.value) = 'array'" +
                            ") r WHERE (r.address, r.tap_uuid, r.frequency) " + onCombination + " GROUP BY r.address")
                    .bindList("addresses", clients)
                    .bindList("taps", taps)
                    .bind("tr_from", timeRange.from())
                    .bind("tr_to", timeRange.to())
                    .map((rs, ctx) -> Map.entry(rs.getString("address"), rs.getLong("frames")))
                    .forEach(entry -> frames.put(entry.getKey(), entry.getValue()));
        }

        return frames;
    }

}
