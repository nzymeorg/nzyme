package app.nzyme.core.search;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.responses.search.SearchFactResponse;
import app.nzyme.core.rest.responses.search.SearchGroupResponse;
import app.nzyme.core.rest.responses.search.SearchHitResponse;
import app.nzyme.core.rest.responses.search.SearchInterpretationResponse;
import app.nzyme.core.rest.responses.search.SearchResponse;
import app.nzyme.core.rest.responses.search.SearchSourceResponse;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SearchService {

    private static final Logger LOG = LogManager.getLogger(SearchService.class);

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAXIMUM_LIMIT = 50;
    public static final int MAXIMUM_OFFSET = 1000;

    private static final int INSTANT_TIMEOUT_MS = 2000;
    private static final int INSTANT_BUDGET_MS = 4000;
    private static final int RECORDED_TIMEOUT_MS = 8000;
    private static final int RECORDED_SLOT_WAIT_SECONDS = 2;
    private static final int RECORDED_PER_USER = 4;

    private static final int INSTANT_CACHE_SECONDS = 15;
    private static final int RECORDED_CACHE_SECONDS = 120;
    private static final int TIMEOUT_CACHE_SECONDS = 15;
    private static final long CACHE_MAXIMUM_HITS = 10_000;
    private static final long TIMEOUT_CACHE_MAXIMUM_SIZE = 5_000;

    private static final String QUERY_CANCELED = "57014";

    // Networks match the addresses in them and show the network, which says enough.
    private static final Set<String> CONTAINING = Set.of("CONTEXT_NETWORK", "SEGMENT");

    public static final String STATUS_OK = "OK";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_BUSY = "BUSY";
    public static final String STATUS_ERROR = "ERROR";

    public static class OutOfTimeException extends RuntimeException { }

    private final NzymeNode nzyme;
    private final List<SearchSource> sources;

    private final Cache<String, SearchGroupResponse> instantCache;
    private final Cache<String, SearchGroupResponse> recordedCache;
    private final Cache<String, SearchGroupResponse> timeoutCache;

    // Scans of recorded data are the expensive part. They must not take the connection pool from everything else.
    private final Semaphore recordedSlots;

    // Scans of one user, waiting or running. A request the client gave up on still runs here until it is done.
    private final ConcurrentHashMap<UUID, Integer> recordedByUser = new ConcurrentHashMap<>();

    private final Timer instantTimer;
    private final Timer recordedTimer;

    public SearchService(NzymeNode nzyme) {
        this(nzyme, SearchSources.all());
    }

    public SearchService(NzymeNode nzyme, List<SearchSource> sources) {
        this.nzyme = nzyme;
        this.sources = List.copyOf(sources);

        this.instantCache = CacheBuilder.newBuilder()
                .expireAfterWrite(INSTANT_CACHE_SECONDS, TimeUnit.SECONDS)
                .maximumWeight(CACHE_MAXIMUM_HITS)
                .weigher((String key, SearchGroupResponse group) -> 1 + group.hits().size())
                .build();
        this.recordedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(RECORDED_CACHE_SECONDS, TimeUnit.SECONDS)
                .maximumWeight(CACHE_MAXIMUM_HITS)
                .weigher((String key, SearchGroupResponse group) -> 1 + group.hits().size())
                .build();
        this.timeoutCache = CacheBuilder.newBuilder()
                .expireAfterWrite(TIMEOUT_CACHE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(TIMEOUT_CACHE_MAXIMUM_SIZE)
                .build();

        int poolSize = nzyme.getConfiguration().performance().databasePoolSize();
        this.recordedSlots = new Semaphore(Math.max(2, poolSize / 4), true);

        this.instantTimer = nzyme.getMetrics().timer(MetricNames.SEARCH_INSTANT_TIMER);
        this.recordedTimer = nzyme.getMetrics().timer(MetricNames.SEARCH_RECORDED_TIMER);
    }

    public SearchResponse search(SearchTerm term, SearchScope scope, int limit, @Nullable SearchWindow window) {
        List<SearchGroupResponse> groups = Lists.newArrayList();
        List<SearchSourceResponse> pending = Lists.newArrayList();

        // All of the quick sources together answer one request, so they share one budget of time.
        long deadline = System.currentTimeMillis() + INSTANT_BUDGET_MS;

        try (Timer.Context ignored = instantTimer.time()) {
            for (SearchSource source : sources) {
                if (!source.applies(term, scope)) {
                    continue;
                }

                if (source.tier() == SearchSource.Tier.RECORDED) {
                    pending.add(SearchSourceResponse.create(source.id(), source.title(), sources.indexOf(source)));
                    continue;
                }

                SearchGroupResponse group = run(source,
                        new SearchSource.Request(term, scope, limit, 0, window, deadline), null);
                if (!group.hits().isEmpty() || !group.status().equals(STATUS_OK)) {
                    groups.add(group);
                }
            }
        }

        return SearchResponse.create(term.raw(), interpret(term, scope), groups, pending);
    }

    public Optional<SearchGroupResponse> searchSource(String sourceId,
                                                      SearchTerm term,
                                                      SearchScope scope,
                                                      int limit,
                                                      int offset,
                                                      @Nullable SearchWindow window,
                                                      UUID requester) {
        for (SearchSource source : sources) {
            if (source.id().equals(sourceId)) {
                if (!source.applies(term, scope)) {
                    return Optional.empty();
                }

                long timeout = source.tier() == SearchSource.Tier.RECORDED
                        ? RECORDED_TIMEOUT_MS : INSTANT_TIMEOUT_MS;

                return Optional.of(run(source, new SearchSource.Request(term, scope, limit, offset, window,
                        System.currentTimeMillis() + timeout), requester));
            }
        }

        return Optional.empty();
    }

    private SearchGroupResponse run(SearchSource source, SearchSource.Request request, @Nullable UUID requester) {
        boolean recorded = source.tier() == SearchSource.Tier.RECORDED;
        Cache<String, SearchGroupResponse> cache = recorded ? recordedCache : instantCache;
        String key = cacheKey(source, request);

        SearchGroupResponse cached = cached(cache, key);
        if (cached != null) {
            return cached;
        }

        if (!recorded) {
            boolean fullTime = request.deadline() - System.currentTimeMillis() >= INSTANT_TIMEOUT_MS;
            return keep(cache, key, execute(source, request), fullTime);
        }

        if (requester != null && !admit(requester)) {
            return failed(source, request, STATUS_BUSY, 0);
        }

        try {
            boolean acquired;
            try {
                acquired = recordedSlots.tryAcquire(RECORDED_SLOT_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                acquired = false;
            }

            if (!acquired) {
                return failed(source, request, STATUS_BUSY, 0);
            }

            try (Timer.Context ignored = recordedTimer.time()) {
                // An identical search may have finished while this one waited for a slot.
                cached = cached(cache, key);
                if (cached != null) {
                    return cached;
                }

                // The time of a scan starts with its slot. Waiting for one takes nothing from it.
                long deadline = System.currentTimeMillis() + RECORDED_TIMEOUT_MS;
                return keep(cache, key, execute(source, request.withDeadline(deadline)), true);
            } finally {
                recordedSlots.release();
            }
        } finally {
            if (requester != null) {
                recordedByUser.computeIfPresent(requester, (id, scans) -> scans <= 1 ? null : scans - 1);
            }
        }
    }

    private boolean admit(UUID requester) {
        if (recordedByUser.merge(requester, 1, Integer::sum) <= RECORDED_PER_USER) {
            return true;
        }

        recordedByUser.computeIfPresent(requester, (id, scans) -> scans <= 1 ? null : scans - 1);
        return false;
    }

    @Nullable
    private SearchGroupResponse cached(Cache<String, SearchGroupResponse> cache, String key) {
        SearchGroupResponse cached = cache.getIfPresent(key);
        return cached == null ? timeoutCache.getIfPresent(key) : cached;
    }

    /*
     * A timeout is kept for a short time: the same search would only time out again and take a slot while it does.
     * Only if the source had all of its time, though. One that ran out of the time its request shared with other
     * sources may well finish on its own.
     */
    private SearchGroupResponse keep(Cache<String, SearchGroupResponse> cache,
                                     String key,
                                     SearchGroupResponse group,
                                     boolean fullTime) {
        if (group.status().equals(STATUS_OK)) {
            cache.put(key, group);
        } else if (group.status().equals(STATUS_TIMEOUT) && fullTime) {
            timeoutCache.put(key, group);
        }

        return group;
    }

    private SearchGroupResponse execute(SearchSource source, SearchSource.Request request) {
        long started = System.currentTimeMillis();
        long deadline = Math.min(request.deadline(), started
                + (source.tier() == SearchSource.Tier.RECORDED ? RECORDED_TIMEOUT_MS : INSTANT_TIMEOUT_MS));

        if (deadline <= started) {
            return failed(source, request, STATUS_TIMEOUT, 0);
        }

        try {
            // Sources set the statement timeout before each statement, from what is left until the deadline.
            SearchSource.Result result = nzyme.getDatabase().inTransaction(handle -> {
                handle.execute("SET TRANSACTION READ ONLY");
                return source.search(handle, request.withDeadline(deadline));
            });

            List<SearchHitResponse> hits = result.hits().stream()
                    .map(hit -> explained(hit, request.term()))
                    .toList();

            return SearchGroupResponse.create(
                    source.id(),
                    source.title(),
                    source.tier().name(),
                    sources.indexOf(source),
                    STATUS_OK,
                    hits,
                    result.hasMore() && request.offset() + request.limit() <= MAXIMUM_OFFSET,
                    result.hasMore() && request.offset() + request.limit() > MAXIMUM_OFFSET,
                    request.offset(),
                    request.limit(),
                    result.searchedFrom(),
                    result.allLink(),
                    System.currentTimeMillis() - started
            );
        } catch (Exception e) {
            long took = System.currentTimeMillis() - started;

            if (isTimeout(e)) {
                LOG.debug("Search source [{}] timed out after {}ms.", source.id(), took);
                return failed(source, request, STATUS_TIMEOUT, took);
            }

            // Every search of a moment fails like this when the connection pool is empty. One line each is enough.
            if (isPoolExhausted(e)) {
                LOG.warn("Search source [{}] got no database connection.", source.id());
                return failed(source, request, STATUS_BUSY, took);
            }

            LOG.error("Search source [{}] failed.", source.id(), e);
            return failed(source, request, STATUS_ERROR, took);
        }
    }

    /*
     * An address can match through something a hit does not show, like a victim of a finding or a device of a
     * beacon. Such a hit says that it involves the address, or it would look like it does not belong.
     */
    private static SearchHitResponse explained(SearchHitResponse hit, SearchTerm term) {
        String address = term.is(SearchTerm.Kind.MAC) ? term.mac()
                : term.is(SearchTerm.Kind.IP) ? term.address() : null;
        if (address == null || CONTAINING.contains(hit.type())) {
            return hit;
        }

        String needle = address.toLowerCase(Locale.ROOT);
        boolean shown = hit.title().toLowerCase(Locale.ROOT).contains(needle)
                || (hit.subtitle() != null && hit.subtitle().toLowerCase(Locale.ROOT).contains(needle))
                || hit.facts().stream().anyMatch(fact -> fact.value().toLowerCase(Locale.ROOT).contains(needle));
        if (shown) {
            return hit;
        }

        List<SearchFactResponse> facts = Lists.newArrayList(SearchFactResponse.create("Involves", address));
        facts.addAll(hit.facts().subList(0, Math.min(hit.facts().size(), 3)));

        return SearchHitResponse.create(hit.type(), hit.title(), hit.subtitle(), facts, hit.timestamp(),
                hit.score(), hit.link());
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof OutOfTimeException
                    || (cause instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState()))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPoolExhausted(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException) {
                return true;
            }
        }

        return false;
    }

    private SearchGroupResponse failed(SearchSource source,
                                       SearchSource.Request request,
                                       String status,
                                       long tookMs) {
        return SearchGroupResponse.create(source.id(), source.title(), source.tier().name(),
                sources.indexOf(source), status, List.of(), false, false, request.offset(), request.limit(), null,
                null, tookMs);
    }

    // The term is free text and comes last, so nothing of it can be read as one of the fields before it.
    private static String cacheKey(SearchSource source, SearchSource.Request request) {
        boolean windowed = source.tier() == SearchSource.Tier.RECORDED && request.window() != null;

        String key = source.id() + "|" + request.scope().fingerprint() + "|" + request.limit() + "|"
                + request.offset() + "|" + (windowed ? request.window().key().length() + ":" + request.window().key()
                : "default") + "|" + request.term().cacheKey();

        return Hashing.sha256().hashString(key, StandardCharsets.UTF_8).toString();
    }

    private List<SearchInterpretationResponse> interpret(SearchTerm term, SearchScope scope) {
        List<SearchInterpretationResponse> interpretations = Lists.newArrayList();

        if (term.is(SearchTerm.Kind.MAC)) {
            interpretations.add(SearchInterpretationResponse.create("MAC_ADDRESS", term.mac(),
                    nzyme.getOuiService().lookup(term.mac()).orElse(null), null));
        }

        if (term.is(SearchTerm.Kind.MAC_PREFIX)) {
            String prefix = term.macPrefix().substring(0, term.macPrefix().length() - 1);
            String vendor = null;
            if (prefix.length() >= 8) {
                vendor = nzyme.getOuiService().lookup(prefix.substring(0, 8) + ":00:00:00").orElse(null);
            }
            interpretations.add(SearchInterpretationResponse.create("MAC_ADDRESS_PREFIX", prefix, vendor, null));
        }

        if (term.is(SearchTerm.Kind.IP)) {
            interpretations.add(SearchInterpretationResponse.create("IP_ADDRESS", term.address(),
                    describeAddress(term.address(), scope),
                    scope.hasSubsystem(Subsystem.ETHERNET) && scope.hasTaps()
                            ? BriefingLinks.to("IP_ADDRESS", "address", term.address()) : null));
        }

        if (term.is(SearchTerm.Kind.CIDR)) {
            interpretations.add(SearchInterpretationResponse.create("NETWORK", term.cidr(), null, null));
        }

        if (term.is(SearchTerm.Kind.UUID)) {
            interpretations.add(SearchInterpretationResponse.create("UUID", term.uuid().toString(), null, null));
        }

        if (term.is(SearchTerm.Kind.JA4)) {
            interpretations.add(SearchInterpretationResponse.create("JA4", term.lower(), null, null));
        } else if (term.is(SearchTerm.Kind.HASH) && !term.is(SearchTerm.Kind.MAC)) {
            interpretations.add(SearchInterpretationResponse.create("HASH", term.lower(), null, null));
        }

        return interpretations;
    }

    @Nullable
    private String describeAddress(String address, SearchScope scope) {
        InetAddress parsed = InetAddresses.forString(address);

        if (parsed.isLoopbackAddress()) {
            return "Loopback address";
        }
        if (parsed.isMulticastAddress()) {
            return "Multicast address";
        }
        if (nzyme.getEthernet().internalAddresses().of(scope.organizationId(), scope.tenantId()).isSiteLocal(parsed)
                || parsed.isLinkLocalAddress()) {
            return "Internal address";
        }

        Optional<GeoIpLookupResult> geo = nzyme.getGeoIpService().lookup(parsed);
        if (geo.isEmpty()) {
            return null;
        }

        List<String> parts = Lists.newArrayList();
        if (geo.get().asn() != null && !Strings.isNullOrEmpty(geo.get().asn().name())) {
            parts.add(geo.get().asn().name());
        }
        if (geo.get().geo() != null && !Strings.isNullOrEmpty(geo.get().geo().countryName())) {
            parts.add(geo.get().geo().countryName());
        }

        return parts.isEmpty() ? null : String.join(", ", parts);
    }

}
