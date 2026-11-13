package app.nzyme.core.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingSinceMode;
import app.nzyme.core.briefing.BriefingWindow;
import app.nzyme.core.briefing.collectors.LocationsCollector;
import app.nzyme.core.briefing.collectors.TapsCollector;
import app.nzyme.core.dashboards.db.DashboardEntry;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.dot11.db.BSSIDAndSSIDCountHistogramEntry;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.rest.responses.briefing.BriefingHistogramBucketResponse;
import app.nzyme.core.rest.responses.dashboards.*;
import app.nzyme.core.taps.CaptureSummary;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.taps.TapManager;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DashboardContent {

    private static final Logger LOG = LogManager.getLogger(DashboardContent.class);

    // Displays and viewers of the same dashboard share one result. No display refreshes more often than this.
    private static final int CACHE_SECONDS = DisplaySettings.MINIMUM_REFRESH_SECONDS;

    private static final long ACTIVE_ASSETS_BUCKET_MS = 60 * 60 * 1000;

    public static final int MAXIMUM_ALERTS = 25;
    // Names are sent for as many taps as a large panel can show by name. The screen decides if they fit.
    public static final int MAXIMUM_TAP_CELLS = 250;
    public static final int MAXIMUM_TAP_PROBLEMS = 50;
    public static final int MAXIMUM_LOCATIONS = 100;

    private static final long DEGRADED_CLOCK_DRIFT_MS = 5000;

    // Ordered worst first.
    private enum TapState {

        OFFLINE('X'),
        ALERT('A'),
        DEGRADED('D'),
        OK('O');

        private final char code;

        TapState(char code) {
            this.code = code;
        }

        private static int rank(char code) {
            for (TapState state : values()) {
                if (state.code == code) {
                    return state.ordinal();
                }
            }

            return values().length;
        }

    }

    private record CachedMetric(long builtAt, @Nullable MetricWidgetResponse metric) { }

    private final NzymeNode nzyme;
    private final Cache<String, DashboardContentResponse> cache;
    private final Cache<String, CachedMetric> metricCache;
    private final Timer buildTimer;

    public DashboardContent(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.buildTimer = nzyme.getMetrics().timer(MetricNames.DASHBOARD_CONTENT_BUILD_TIMER);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
        this.metricCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    public DashboardContentResponse get(DashboardEntry dashboard, boolean redacted) {
        DashboardContentResponse content;
        try {
            content = cache.get(cacheKey(dashboard), () -> {
                try (Timer.Context ignored = buildTimer.time()) {
                    return build(dashboard);
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not build content of dashboard.", e.getCause());
        }

        return redacted ? redacted(content) : content;
    }

    // A changed dashboard gets new keys, so an edit shows up with the next request.
    private static String cacheKey(DashboardEntry dashboard) {
        return dashboard.id() + ":" + dashboard.updatedAt().getMillis();
    }

    private static long metricCacheSeconds(int windowMinutes) {
        if (windowMinutes <= 60) {
            return 30;
        } else if (windowMinutes <= 240) {
            return 60;
        } else if (windowMinutes <= 1440) {
            return 120;
        } else {
            return 600;
        }
    }

    // Half as many samples where each one is drawn as a bar of its own.
    static long sampleSizeMs(int windowMinutes, DashboardMetric.Aggregation aggregation) {
        long minutes;
        if (windowMinutes <= 60) {
            minutes = 1;
        } else if (windowMinutes <= 240) {
            minutes = 2;
        } else if (windowMinutes <= 1440) {
            minutes = 10;
        } else {
            minutes = 60;
        }

        return (aggregation == DashboardMetric.Aggregation.SUM && windowMinutes > 60 ? minutes * 2 : minutes) * 60 * 1000;
    }

    private static DashboardContentResponse redacted(DashboardContentResponse content) {
        Map<String, Object> data = Maps.newHashMap();
        for (Map.Entry<String, Object> entry : content.data().entrySet()) {
            if (entry.getValue() instanceof AlertsWidgetResponse alerts) {
                // The details name the addresses, networks and hosts an alert is about.
                data.put(entry.getKey(), AlertsWidgetResponse.create(alerts.total(), alerts.alerts().stream()
                        .map(a -> AlertsWidgetAlertResponse.create(
                                a.title(), a.subsystem(), null, a.tapName(), a.firstSeen(), a.lastSeen()))
                        .toList()));
            } else {
                data.put(entry.getKey(), entry.getValue());
            }
        }

        // The filters of a topology widget can name addresses. The screen does not need them.
        List<DashboardWidget> widgets = content.widgets().stream()
                .map(w -> {
                    if (w.options() == null || !w.options().containsKey("filters")) {
                        return w;
                    }

                    Map<String, Object> options = Maps.newHashMap(w.options());
                    options.remove("filters");
                    return new DashboardWidget(w.id(), w.type(), w.x(), w.y(), w.w(), w.h(), options);
                })
                .toList();

        return DashboardContentResponse.create(content.generatedAt(), content.windowMinutes(), content.scopeType(),
                content.scopeCount(), true, content.tapCount(), content.condition(), widgets, data);
    }

    public List<Tap> tapsInScope(DashboardEntry dashboard) {
        DashboardSettings settings = dashboard.settings();
        List<Tap> taps = nzyme.getTapManager().findAllTapsOfTenant(dashboard.organizationId(), dashboard.tenantId());

        return switch (settings.scope()) {
            case ALL -> taps;
            case LOCATIONS -> taps.stream()
                    .filter(t -> t.locationId() != null && settings.scopeIds().contains(t.locationId()))
                    .collect(Collectors.toList());
            case TAPS -> taps.stream()
                    .filter(t -> settings.scopeIds().contains(t.uuid()))
                    .collect(Collectors.toList());
        };
    }

    private DashboardContentResponse build(DashboardEntry dashboard) {
        long started = System.currentTimeMillis();

        Build build = new Build(dashboard);
        Map<String, Object> data = Maps.newHashMap();
        for (DashboardWidget widget : dashboard.settings().widgets()) {
            data.put(widget.id(), build.widget(widget));
        }

        LOG.debug("Built content of dashboard [{}] in {}ms.", dashboard.uuid(), System.currentTimeMillis() - started);

        return DashboardContentResponse.create(
                build.to,
                dashboard.settings().windowMinutes(),
                dashboard.settings().scope().name(),
                build.scopeCount(),
                false,
                build.taps.size(),
                // A dashboard only tells about alerts if someone who may see alerts put them on it.
                build.status(dashboard.settings().showsAlerts()),
                dashboard.settings().widgets(),
                data
        );
    }

    private class Build {

        private final DashboardEntry dashboard;

        private final UUID organizationId;
        private final UUID tenantId;
        private final List<Tap> taps;
        private final List<UUID> tapIds;
        private final boolean allTaps;

        private final DateTime from;
        private final DateTime to;
        private final DateTime previousFrom;
        private final TimeRange combined;
        private final Bucketing.BucketingConfiguration bucketing;

        private final Map<Optional<Subsystem>, Long> activeAlerts = Maps.newHashMap();
        private Map<UUID, Long> alertsByTap;
        private List<BSSIDAndSSIDCountHistogramEntry> bssidAndSsidHistogram;
        private List<TenantLocationEntry> tenantLocations;
        private TapsWidgetResponse tapsWidget;
        private LocationsWidgetResponse locationsWidget;

        private Build(DashboardEntry dashboard) {
            this.dashboard = dashboard;

            this.organizationId = dashboard.organizationId();
            this.tenantId = dashboard.tenantId();
            this.taps = tapsInScope(dashboard);
            this.tapIds = taps.stream().map(Tap::uuid).collect(Collectors.toList());
            this.allTaps = dashboard.settings().scope() == DashboardSettings.Scope.ALL;

            this.to = DateTime.now();
            this.from = to.minusMinutes(dashboard.settings().windowMinutes());
            this.previousFrom = from.minusMinutes(dashboard.settings().windowMinutes());
            this.combined = TimeRange.create(previousFrom, to, false);
            // Finer than the general rule for charts, which would give a day one sample per hour.
            this.bucketing = Bucketing.BucketingConfiguration.create(dashboard.settings().windowMinutes() <= 1440
                    ? Bucketing.Type.MINUTE : Bucketing.Type.HOUR);
        }

        @Nullable
        private Object widget(DashboardWidget widget) {
            return switch (widget.widgetType()) {
                case STATUS -> status(true);
                case ALERTS -> alerts(alertSubsystem(widget));
                case TAPS -> {
                    if (tapsWidget == null) {
                        tapsWidget = taps();
                    }
                    yield tapsWidget;
                }
                case METRIC -> cachedMetric(DashboardMetric.valueOf(widget.option("metric")), alertSubsystem(widget));
                // Clocks and texts are drawn from their options alone. The topology has a feed of its own.
                case CLOCK, TEXT, TOPOLOGY -> null;
                case LOCATIONS -> {
                    if (locationsWidget == null) {
                        locationsWidget = locations();
                    }
                    yield locationsWidget;
                }
            };
        }

        private List<TenantLocationEntry> tenantLocations() {
            if (tenantLocations == null) {
                tenantLocations = nzyme.getAuthenticationService()
                        .findAllTenantLocations(organizationId, tenantId, Integer.MAX_VALUE, 0);
            }

            return tenantLocations;
        }

        // Scope IDs of locations and taps that were deleted since do not count.
        private int scopeCount() {
            return switch (dashboard.settings().scope()) {
                case ALL -> 0;
                case TAPS -> taps.size();
                case LOCATIONS -> (int) tenantLocations().stream()
                        .filter(l -> dashboard.settings().scopeIds().contains(l.uuid()))
                        .count();
            };
        }

        @Nullable
        private Subsystem alertSubsystem(DashboardWidget widget) {
            String subsystem = widget.option("subsystem");
            return subsystem == null ? null : Subsystem.valueOf(subsystem);
        }

        private boolean disabled(@Nullable Subsystem subsystem) {
            return subsystem != null && !nzyme.getSubsystems().isEnabled(subsystem, organizationId, tenantId);
        }

        private long activeAlerts() {
            return activeAlerts(null);
        }

        private long activeAlerts(@Nullable Subsystem subsystem) {
            return activeAlerts.computeIfAbsent(Optional.ofNullable(subsystem), s -> nzyme.getDetectionAlertService()
                    .countActiveAlertsInScope(organizationId, tenantId, tapIds, allTaps, subsystem));
        }

        private Map<UUID, Long> alertsByTap() {
            if (alertsByTap == null) {
                alertsByTap = nzyme.getDetectionAlertService().countActiveAlertsByTap(organizationId, tenantId, tapIds);
            }

            return alertsByTap;
        }

        private long tapsOnline() {
            return taps.stream().filter(t -> Tools.isTapActive(t.lastReport())).count();
        }

        private StatusWidgetResponse status(boolean withAlerts) {
            long online = tapsOnline();

            String level;
            if (withAlerts && activeAlerts() > 0) {
                level = "ALERT";
            } else if (taps.isEmpty() || online < taps.size()) {
                level = "WARN";
            } else {
                level = "OK";
            }

            return StatusWidgetResponse.create(level, withAlerts ? activeAlerts() : null, online, taps.size());
        }

        @Nullable
        private AlertsWidgetResponse alerts(@Nullable Subsystem subsystem) {
            if (disabled(subsystem)) {
                return null;
            }

            Map<UUID, String> tapNames = taps.stream().collect(Collectors.toMap(Tap::uuid, Tap::name));

            List<AlertsWidgetAlertResponse> alerts = Lists.newArrayList();
            for (DetectionAlertEntry alert : nzyme.getDetectionAlertService()
                    .findActiveAlertsInScope(organizationId, tenantId, tapIds, allTaps, subsystem, MAXIMUM_ALERTS)) {
                String title;
                try {
                    title = DetectionType.valueOf(alert.detectionType()).getTitle();
                } catch (IllegalArgumentException e) {
                    title = alert.detectionType();
                }

                alerts.add(AlertsWidgetAlertResponse.create(
                        title,
                        alert.subsystem(),
                        alert.details(),
                        alert.tapId() == null ? null : tapNames.get(alert.tapId()),
                        alert.createdAt(),
                        alert.lastSeen()
                ));
            }

            return AlertsWidgetResponse.create(activeAlerts(subsystem), alerts);
        }

        private TapsWidgetResponse taps() {
            // Which taps have alerts is alert data: only on a dashboard that someone who may see alerts put them on.
            Map<UUID, Long> alertsByTap = dashboard.settings().showsAlerts() ? alertsByTap() : Map.of();
            Map<UUID, CaptureSummary> captures = nzyme.getTapManager().summarizeActiveCapturesOfTaps(tapIds);

            Map<UUID, String> locationNames = Maps.newHashMap();
            for (TenantLocationEntry location : tenantLocations()) {
                locationNames.put(location.uuid(), location.name());
            }

            List<TapsWidgetTapResponse> all = Lists.newArrayList();
            for (Tap tap : taps) {
                CaptureSummary capture = captures.getOrDefault(tap.uuid(), CaptureSummary.NONE);
                long alerts = alertsByTap.getOrDefault(tap.uuid(), 0L);
                boolean online = Tools.isTapActive(tap.lastReport());

                TapState state;
                String reason = null;
                if (!online) {
                    state = TapState.OFFLINE;
                    reason = "OFFLINE";
                } else if (alerts > 0) {
                    state = TapState.ALERT;
                    reason = "ALERTS";
                } else if (capture.running() < capture.total()) {
                    state = TapState.DEGRADED;
                    reason = "CAPTURES_STOPPED";
                } else if (capture.dropping() > 0) {
                    state = TapState.DEGRADED;
                    reason = "DROPS";
                } else if (tap.clockDriftMs() != null && Math.abs(tap.clockDriftMs()) >= DEGRADED_CLOCK_DRIFT_MS) {
                    state = TapState.DEGRADED;
                    reason = "CLOCK_DRIFT";
                } else {
                    state = TapState.OK;
                }

                all.add(TapsWidgetTapResponse.create(
                        tap.name(),
                        tap.locationId() == null ? null : locationNames.get(tap.locationId()),
                        state.name(),
                        reason,
                        tap.lastReport(),
                        tap.clockDriftMs(),
                        capture.dropped(),
                        capture.running(),
                        capture.total(),
                        alerts
                ));
            }

            Comparator<TapsWidgetTapResponse> worstFirst = Comparator
                    .comparing((TapsWidgetTapResponse t) -> TapState.valueOf(t.state()).ordinal())
                    .thenComparing(t -> t.name().toLowerCase());

            // One character per tap and location, worst first. Grouped by ID: two locations can have the same name.
            Map<UUID, List<TapsWidgetTapResponse>> byLocation = Maps.newHashMap();
            List<TapsWidgetTapResponse> withoutLocation = Lists.newArrayList();
            for (int i = 0; i < taps.size(); i++) {
                UUID locationId = taps.get(i).locationId();
                if (locationId == null || !locationNames.containsKey(locationId)) {
                    withoutLocation.add(all.get(i));
                } else {
                    byLocation.computeIfAbsent(locationId, k -> Lists.newArrayList()).add(all.get(i));
                }
            }

            List<TapsWidgetLocationResponse> locations = byLocation.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<UUID, List<TapsWidgetTapResponse>> e) ->
                            locationNames.get(e.getKey()).toLowerCase()))
                    .map(e -> location(locationNames.get(e.getKey()), e.getValue(), worstFirst))
                    .collect(Collectors.toList());
            if (!withoutLocation.isEmpty()) {
                locations.add(location(null, withoutLocation, worstFirst));
            }
            locations.sort(Comparator
                    .comparing((TapsWidgetLocationResponse l) -> TapState.rank(l.states().charAt(0)))
                    .thenComparing(l -> -(l.total() - l.online())));

            List<TapsWidgetCellResponse> cells = null;
            if (all.size() <= MAXIMUM_TAP_CELLS) {
                cells = all.stream()
                        .sorted(Comparator.comparing(t -> t.name().toLowerCase()))
                        .map(t -> TapsWidgetCellResponse.create(t.name(), t.state()))
                        .toList();
            }

            return TapsWidgetResponse.create(
                    all.size(),
                    tapsOnline(),
                    all.stream().filter(t -> t.state().equals(TapState.OFFLINE.name())).count(),
                    all.stream().filter(t -> t.state().equals(TapState.DEGRADED.name())).count(),
                    all.stream().filter(t -> t.state().equals(TapState.ALERT.name())).count(),
                    locations,
                    cells,
                    all.stream()
                            .filter(t -> !t.state().equals(TapState.OK.name()))
                            .sorted(worstFirst)
                            .limit(MAXIMUM_TAP_PROBLEMS)
                            .toList()
            );
        }

        private TapsWidgetLocationResponse location(@Nullable String name,
                                                    List<TapsWidgetTapResponse> taps,
                                                    Comparator<TapsWidgetTapResponse> worstFirst) {
            StringBuilder states = new StringBuilder();
            taps.stream().sorted(worstFirst).forEach(t -> states.append(TapState.valueOf(t.state()).code));

            return TapsWidgetLocationResponse.create(
                    name,
                    taps.size(),
                    (int) taps.stream().filter(t -> !t.state().equals(TapState.OFFLINE.name())).count(),
                    states.toString()
            );
        }

        private LocationsWidgetResponse locations() {
            DashboardSettings settings = dashboard.settings();

            record Row(TenantLocationEntry location, int taps, int online, long alerts) { }

            List<Row> rows = Lists.newArrayList();
            for (TenantLocationEntry location : tenantLocations()) {
                List<Tap> tapsAtLocation = taps.stream().filter(t -> location.uuid().equals(t.locationId())).toList();

                boolean inScope = switch (settings.scope()) {
                    case ALL -> true;
                    case LOCATIONS -> settings.scopeIds().contains(location.uuid());
                    case TAPS -> !tapsAtLocation.isEmpty();
                };
                if (!inScope) {
                    continue;
                }

                rows.add(new Row(
                        location,
                        tapsAtLocation.size(),
                        (int) tapsAtLocation.stream().filter(t -> Tools.isTapActive(t.lastReport())).count(),
                        tapsAtLocation.stream().mapToLong(t -> alertsByTap().getOrDefault(t.uuid(), 0L)).sum()
                ));
            }

            // Time zone and weather are only looked up for the locations that are returned.
            return LocationsWidgetResponse.create(rows.size(), rows.stream()
                    .sorted(Comparator
                            .comparing((Row r) -> -r.alerts())
                            .thenComparing(r -> -(r.taps() - r.online()))
                            .thenComparing(r -> r.location().name().toLowerCase()))
                    .limit(MAXIMUM_LOCATIONS)
                    .map(r -> LocationsWidgetLocationResponse.create(r.location().name(), r.taps(), r.online(),
                            r.alerts(), timeZone(r.location()), LocationsCollector.environment(nzyme, r.location())))
                    .toList());
        }

        @Nullable
        private String timeZone(TenantLocationEntry location) {
            if (location.latitude() == null || location.longitude() == null
                    || location.latitude() == 0 || location.longitude() == 0) {
                return null;
            }

            return nzyme.getEnvironmentService()
                    .getTimezoneAtCoordinates(location.longitude(), location.latitude())
                    .map(ZoneId::getId)
                    .orElse(null);
        }

        @Nullable
        private MetricWidgetResponse cachedMetric(DashboardMetric metric, @Nullable Subsystem alertSubsystem) {
            // Alert counts stay as fresh as the alerts next to them.
            if (metric.showsAlerts()) {
                return metric(metric, alertSubsystem);
            }

            String key = cacheKey(dashboard) + ":" + metric.name();
            CachedMetric cached = metricCache.getIfPresent(key);
            long maximumAge = metricCacheSeconds(dashboard.settings().windowMinutes()) * 1000;
            if (cached != null && System.currentTimeMillis() - cached.builtAt() < maximumAge) {
                return cached.metric();
            }

            MetricWidgetResponse built = metric(metric, null);
            metricCache.put(key, new CachedMetric(System.currentTimeMillis(), built));

            return built;
        }

        private List<BSSIDAndSSIDCountHistogramEntry> bssidAndSsidHistogram() {
            if (bssidAndSsidHistogram == null) {
                bssidAndSsidHistogram = nzyme.getDot11().getBSSIDAndSSIDCountHistogram(
                        combined, bucketing, Filters.create(Maps.newHashMap()), tapIds);
            }

            return bssidAndSsidHistogram;
        }

        @Nullable
        private MetricWidgetResponse metric(DashboardMetric metric, @Nullable Subsystem alertSubsystem) {
            if (disabled(metric.subsystem()) || disabled(alertSubsystem)) {
                return null;
            }

            DetectionAlertService alerts = nzyme.getDetectionAlertService();
            Filters noFilters = Filters.create(Maps.newHashMap());

            return switch (metric) {
                case ALERTS_ACTIVE -> response(metric, activeAlerts(alertSubsystem), null, List.of());
                case ALERTS_NEW -> response(
                        metric,
                        alerts.countNewAlertsInScope(organizationId, tenantId, tapIds, allTaps, alertSubsystem, from, to),
                        (double) alerts.countNewAlertsInScope(organizationId, tenantId, tapIds, allTaps, alertSubsystem, previousFrom, from),
                        samples(metric, alerts.getNewAlertsHistogramInScope(organizationId, tenantId, tapIds, allTaps, alertSubsystem, from, to, bucketing)
                                .stream()
                                .map(e -> BriefingHistogramBucketResponse.create(e.bucket(), e.value()))
                                .toList())
                );
                case THROUGHPUT -> throughput(metric);
                case ETHERNET_ACTIVE_ASSETS -> response(
                        metric,
                        countActiveAssets(hour(from), to),
                        (double) countActiveAssets(hour(previousFrom), hour(from)),
                        filled(activeAssetsHistogram(), ACTIVE_ASSETS_BUCKET_MS)
                );
                case DOT11_BSSIDS -> aggregated(metric, bssidAndSsidHistogram().stream()
                        .map(e -> BriefingHistogramBucketResponse.create(e.bucket(), e.bssidCount()))
                        .toList());
                case DOT11_SSIDS -> aggregated(metric, bssidAndSsidHistogram().stream()
                        .map(e -> BriefingHistogramBucketResponse.create(e.bucket(), e.ssidCount()))
                        .toList());
                case DOT11_DISCONNECTIONS -> aggregated(metric, nzyme.getDot11()
                        .getDiscoHistogram(combined, bucketing, tapIds, noFilters, null).stream()
                        .map(e -> BriefingHistogramBucketResponse.create(e.bucket(), e.frameCount()))
                        .toList());
                case BLUETOOTH_DEVICES -> aggregated(metric, tapIds.isEmpty() ? List.of() : nzyme.getBluetooth()
                        .getDeviceCountHistogram(combined, bucketing, noFilters, tapIds).stream()
                        .map(e -> BriefingHistogramBucketResponse.create(e.bucket(), e.value()))
                        .toList());
            };
        }

        // Gauges are kept for a day. A longer window would show that day under the name of a week, and a window
        // is only compared with the one before it if that one is still complete.
        @Nullable
        private MetricWidgetResponse throughput(DashboardMetric metric) {
            int windowMinutes = dashboard.settings().windowMinutes();
            int keptMinutes = TapManager.METRICS_RETENTION_HOURS * 60;
            if (windowMinutes > keptMinutes) {
                return null;
            }

            return aggregated(metric, new TapsCollector(briefingContext()).throughputHistogram(
                    windowMinutes * 2 <= keptMinutes ? combined : TimeRange.create(from, to, false),
                    bucketing.type().getDateTruncName()));
        }

        private MetricWidgetResponse aggregated(DashboardMetric metric, List<BriefingHistogramBucketResponse> buckets) {
            // The bucket the window starts in belongs to the current window.
            long currentFrom = from.getMillis() - bucketing.bucketSizeMs();

            List<BriefingHistogramBucketResponse> current = buckets.stream()
                    .filter(b -> b.bucket().getMillis() > currentFrom)
                    .sorted(Comparator.comparing(BriefingHistogramBucketResponse::bucket))
                    .toList();
            List<BriefingHistogramBucketResponse> previous = buckets.stream()
                    .filter(b -> b.bucket().getMillis() <= currentFrom)
                    .toList();

            // No data is not zero: the window before can be older than what is kept.
            Double previousValue = previous.isEmpty() ? null : aggregate(metric, previous);

            return response(metric, aggregate(metric, current), previousValue, samples(metric, current));
        }

        private List<BriefingHistogramBucketResponse> samples(DashboardMetric metric,
                                                              List<BriefingHistogramBucketResponse> buckets) {
            List<BriefingHistogramBucketResponse> filled = filled(buckets, bucketing.bucketSizeMs());
            long sizeMs = sampleSizeMs(dashboard.settings().windowMinutes(), metric.aggregation());
            if (sizeMs <= bucketing.bucketSizeMs()) {
                return filled;
            }

            Map<Long, List<BriefingHistogramBucketResponse>> merged = Maps.newTreeMap();
            for (BriefingHistogramBucketResponse bucket : filled) {
                long start = bucket.bucket().getMillis() - Math.floorMod(bucket.bucket().getMillis(), sizeMs);
                merged.computeIfAbsent(start, s -> Lists.newArrayList()).add(bucket);
            }

            return merged.entrySet().stream()
                    .map(e -> BriefingHistogramBucketResponse.create(new DateTime(e.getKey()), aggregate(metric, e.getValue())))
                    .toList();
        }

        // Queries only return buckets that hold data. The gaps are zeros, and the samples are drawn evenly spaced.
        private List<BriefingHistogramBucketResponse> filled(List<BriefingHistogramBucketResponse> buckets, long sizeMs) {
            if (buckets.isEmpty()) {
                return buckets;
            }

            Map<Long, Double> values = Maps.newHashMap();
            for (BriefingHistogramBucketResponse bucket : buckets) {
                values.put(bucket.bucket().getMillis(), bucket.value());
            }

            // Buckets follow the time zone of the database session, which can be off the hour.
            long offset = Math.floorMod(buckets.get(0).bucket().getMillis(), sizeMs);
            long first = from.getMillis() - Math.floorMod(from.getMillis() - offset, sizeMs);

            List<BriefingHistogramBucketResponse> filled = Lists.newArrayList();
            for (long bucket = first; bucket <= to.getMillis(); bucket += sizeMs) {
                filled.add(BriefingHistogramBucketResponse.create(new DateTime(bucket), values.getOrDefault(bucket, 0D)));
            }

            return filled;
        }

        private double aggregate(DashboardMetric metric, List<BriefingHistogramBucketResponse> buckets) {
            return switch (metric.aggregation()) {
                case SUM -> buckets.stream().mapToDouble(BriefingHistogramBucketResponse::value).sum();
                case AVERAGE -> buckets.stream().mapToDouble(BriefingHistogramBucketResponse::value).average().orElse(0);
                case PEAK -> buckets.stream().mapToDouble(BriefingHistogramBucketResponse::value).max().orElse(0);
                default -> throw new IllegalStateException("Metric [" + metric + "] is not built from buckets.");
            };
        }

        private MetricWidgetResponse response(DashboardMetric metric,
                                              double current,
                                              @Nullable Double previous,
                                              List<BriefingHistogramBucketResponse> samples) {
            return MetricWidgetResponse.create(metric.name(), metric.title(), metric.unit(),
                    metric.aggregation().name(), current, previous, samples);
        }

        // assets_activity holds one row per MAC, tap and UTC hour.
        private static DateTime hour(DateTime time) {
            return time.withZone(DateTimeZone.UTC).hourOfDay().roundFloorCopy();
        }

        private long countActiveAssets(DateTime start, DateTime end) {
            if (tapIds.isEmpty()) {
                return 0;
            }

            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT COUNT(*) FROM (SELECT DISTINCT mac FROM assets_activity " +
                                    "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                    "AND tap_uuid IN (<taps>) AND bucket >= :start AND bucket < :end) AS seen")
                            .bind("organization_id", organizationId)
                            .bind("tenant_id", tenantId)
                            .bindList("taps", tapIds)
                            .bind("start", start)
                            .bind("end", end)
                            .mapTo(Long.class)
                            .one()
            );
        }

        private List<BriefingHistogramBucketResponse> activeAssetsHistogram() {
            if (tapIds.isEmpty()) {
                return List.of();
            }

            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT bucket, COUNT(*) AS value FROM (" +
                                    "SELECT DISTINCT bucket, mac FROM assets_activity " +
                                    "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                    "AND tap_uuid IN (<taps>) AND bucket >= :start AND bucket < :end) AS seen " +
                                    "GROUP BY bucket ORDER BY bucket ASC")
                            .bind("organization_id", organizationId)
                            .bind("tenant_id", tenantId)
                            .bindList("taps", tapIds)
                            .bind("start", hour(from))
                            .bind("end", to)
                            .map((rs, ctx) -> BriefingHistogramBucketResponse.create(
                                    new DateTime(rs.getTimestamp("bucket")), rs.getLong("value")))
                            .list()
            );
        }

        private BriefingContext briefingContext() {
            return new BriefingContext(nzyme, organizationId, tenantId, tapIds,
                    new BriefingWindow(BriefingSinceMode.TIME_RANGE, from, to, false, false, null, null, null),
                    true, false, false, false, false, false, Set.<Subsystem>of());
        }

    }

}
