package app.nzyme.core.dashboards;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dashboards.db.DashboardEntry;
import app.nzyme.core.ethernet.topology.Topology;
import app.nzyme.core.rest.resources.ethernet.TopologyResource;
import app.nzyme.core.rest.responses.dashboards.DashboardTopologyResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyLiveResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyLiveSessionResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyNodeResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterParser;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DashboardTopology {

    private static final Logger LOG = LogManager.getLogger(DashboardTopology.class);

    // Every screen asks every few seconds. Screens that ask within this time of each other share one query.
    private static final long CACHE_MS = 2000;
    // A feed that cannot be built, like one with a filter value the database rejects, is not tried again at once.
    private static final long FAILURE_CACHE_SECONDS = 30;

    // Long enough for a screen that missed a request to find no gap between two feeds.
    public static final int SECONDS = 60;
    public static final int MAXIMUM_SESSIONS = 2000;
    // A feed can hold megabytes. Past this many widgets on screen at once, the oldest feeds are built again.
    private static final int MAXIMUM_CACHED_FEEDS = 250;

    private static class Feed {

        private final long builtAt;
        @Nullable
        private final TopologyLiveResponse live;
        private TopologyLiveResponse redacted;

        private Feed(long builtAt, @Nullable TopologyLiveResponse live) {
            this.builtAt = builtAt;
            this.live = live;
        }

    }

    private final NzymeNode nzyme;
    private final DashboardContent content;
    private final DashboardPseudonyms pseudonyms;
    private final Cache<String, Feed> cache;
    private final Cache<String, Boolean> failures;
    private final Timer buildTimer;

    public DashboardTopology(NzymeNode nzyme, DashboardContent content) {
        this.nzyme = nzyme;
        this.buildTimer = nzyme.getMetrics().timer(MetricNames.DASHBOARD_TOPOLOGY_BUILD_TIMER);
        this.content = content;
        this.pseudonyms = new DashboardPseudonyms(nzyme);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_MS, TimeUnit.MILLISECONDS)
                .maximumSize(MAXIMUM_CACHED_FEEDS)
                .build();
        this.failures = CacheBuilder.newBuilder()
                .expireAfterWrite(FAILURE_CACHE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    public Optional<DashboardTopologyResponse> get(DashboardEntry dashboard, String widgetId, boolean redacted) {
        Optional<DashboardWidget> widget = dashboard.settings().widgets().stream()
                .filter(w -> w.id().equals(widgetId) && w.widgetType() == DashboardWidget.Type.TOPOLOGY)
                .findFirst();
        if (widget.isEmpty()) {
            return Optional.empty();
        }

        // A changed dashboard gets new keys, so an edit shows up with the next request.
        String key = dashboard.id() + ":" + dashboard.updatedAt().getMillis() + ":" + widgetId;

        if (failures.getIfPresent(key) != null) {
            return Optional.of(DashboardTopologyResponse.create(0, redacted, null));
        }

        Feed feed;
        try {
            feed = cache.get(key, () -> new Feed(System.currentTimeMillis(), build(dashboard, widget.get())));
        } catch (ExecutionException | RuntimeException e) {
            LOG.warn("Could not build topology of widget [{}] of dashboard [{}]: {}", widgetId, dashboard.uuid(),
                    Throwables.getRootCause(e).getMessage());
            failures.put(key, true);
            return Optional.of(DashboardTopologyResponse.create(0, redacted, null));
        }

        TopologyLiveResponse live = feed.live;
        if (redacted && live != null) {
            synchronized (feed) {
                if (feed.redacted == null) {
                    feed.redacted = redacted(feed.live, pseudonyms.scope(dashboard.uuid().toString()));
                }
                live = feed.redacted;
            }
        }

        return Optional.of(DashboardTopologyResponse.create(
                Math.max(0, System.currentTimeMillis() - feed.builtAt), redacted, live));
    }

    @Nullable
    private TopologyLiveResponse build(DashboardEntry dashboard, DashboardWidget widget) {
        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, dashboard.organizationId(), dashboard.tenantId())) {
            return null;
        }

        try (Timer.Context ignored = buildTimer.time()) {
            return query(dashboard, widget);
        }
    }

    private TopologyLiveResponse query(DashboardEntry dashboard, DashboardWidget widget) {
        List<Tap> taps = content.tapsInScope(dashboard);
        // The one clock that all nodes of a cluster share.
        DateTime now = nzyme.getDatabase().getDatabaseClock();

        Topology.Selection selection = new Topology.Selection(
                TimeRange.create(now.minusSeconds(SECONDS),
                        now.plusMinutes(TopologyResource.LIVE_TAP_CLOCK_TOLERANCE_MINUTES), false),
                FilterParser.parseFiltersQueryParameter(widget.option("filters")),
                taps.stream().map(Tap::uuid).collect(Collectors.toList()),
                Topology.Scope.ALL,
                List.of(),
                List.of()
        );

        Topology.Live live = nzyme.getEthernet().topology().live(
                selection,
                Topology.InternalLevel.valueOf(widget.option("internal_level")),
                Topology.ExternalLevel.valueOf(widget.option("external_level")),
                MAXIMUM_SESSIONS
        );

        return TopologyLiveResponse.create(
                now,
                SECONDS,
                TopologyResource.nodeResponses(nzyme, live.nodes(), taps,
                        TimeRange.create(now.minusMinutes(TopologyResource.LIVE_ASSET_MINUTES), now, false),
                        dashboard.organizationId(), dashboard.tenantId()),
                List.of(),
                List.of(),
                TopologyResource.liveSessionResponses(live),
                MAXIMUM_SESSIONS,
                live.limited(),
                live.merged()
        );
    }

    // Ports stay: the screen needs them to count traffic that several taps saw once, and they name no one.
    static TopologyLiveResponse redacted(TopologyLiveResponse live, DashboardPseudonyms.Scope pseudonyms) {
        Map<String, String> known = new HashMap<>();
        Map<UUID, UUID> knownTaps = new HashMap<>();

        List<TopologyNodeResponse> nodes = live.nodes().stream()
                .map(n -> TopologyNodeResponse.create(
                        known.computeIfAbsent(n.id(), pseudonyms::of), n.type(), n.level(), null, null, null,
                        n.tapName(), null, null, null, null,
                        Topology.NodeLevel.COUNTRY.name().equals(n.level()) ? n.countryCode() : null))
                .collect(Collectors.toList());

        List<TopologyLiveSessionResponse> sessions = live.sessions().stream()
                .map(s -> TopologyLiveSessionResponse.create(
                        pseudonyms.of(s.sessionKey()),
                        knownTaps.computeIfAbsent(s.tapUuid(), pseudonyms::of),
                        s.l4Type(),
                        known.computeIfAbsent(s.source(), pseudonyms::of),
                        known.computeIfAbsent(s.target(), pseudonyms::of),
                        known.computeIfAbsent(s.sourceAddress(), pseudonyms::of),
                        known.computeIfAbsent(s.destinationAddress(), pseudonyms::of),
                        s.destinationPort(), s.startTime(), s.mostRecentSegmentTime(), s.bytesTx(), s.bytesRx(),
                        null, null, null, null))
                .collect(Collectors.toList());

        return TopologyLiveResponse.create(live.now(), live.seconds(), nodes, List.of(), List.of(), sessions,
                live.limit(), live.limited(), live.mergeTaps());
    }

}
