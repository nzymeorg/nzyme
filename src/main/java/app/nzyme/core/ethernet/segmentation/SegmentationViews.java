package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.InternalAddresses;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SegmentationViews {

    private static final int SERVICES_PER_CELL = 8;
    public static final int FINDINGS_FOR_MATRIX = 5000;
    public static final int PREVIEW_FLOW_LIMIT = 100000;
    public static final int PREVIEW_ROW_LIMIT = 500;
    private static final int SAMPLE_HOSTS = 5;

    public static final String BROADCAST_NAME = "Broadcast";

    public enum Coverage {
        NONE,
        PARTIAL,
        FULL
    }

    public enum PolicyState {
        HOLDING,
        CROSSED,
        ATTEMPTED,
        NOT_OBSERVABLE,
        PAUSED
    }

    public record Service(String l4Type, int port, String protocol, long connected, long rejected, long noReply, long sent) {

        public long total() {
            return connected + rejected + noReply + sent;
        }

    }

    public record Cell(long sourceGroup,
                       long destinationGroup,
                       long connected,
                       long rejected,
                       long noReply,
                       long sent,
                       long continued,
                       List<Service> services,
                       int serviceCount,
                       List<UUID> taps,
                       DateTime firstSeen,
                       DateTime lastSeen,
                       Coverage coverage,
                       List<UUID> policies,
                       long crossedFindings,
                       long attemptedFindings) { }

    public record Matrix(List<Long> groups,
                         List<Cell> cells,
                         boolean restampPending,
                         List<Policy> policies,
                         boolean findingsLimited) { }

    public record PolicyOverview(Policy policy,
                                 PolicyState state,
                                 Findings.PolicySummary summary,
                                 boolean findingsLimitReached) { }

    public record PreviewSide(PolicyRule.Selector selector, String label) { }

    public record PreviewRow(PreviewSide source,
                             PreviewSide destination,
                             String l4Type,
                             int port,
                             String protocol,
                             PolicyMatcher.Verdict verdict,
                             long connected,
                             long rejected,
                             long noReply,
                             long sent,
                             long sourceHosts,
                             long destinationHosts,
                             List<String> sourceSample,
                             List<String> destinationSample,
                             DateTime firstSeen,
                             DateTime lastSeen) {

        public long total() {
            return connected + rejected + noReply + sent;
        }

    }

    public record Preview(List<PreviewRow> rows,
                          long allowedSessions,
                          long crossedSessions,
                          long attemptedSessions,
                          long indeterminateSessions,
                          List<Integer> unusedAllowed,
                          boolean truncated) { }

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    public SegmentationViews(NzymeNode nzyme, Segmentation segmentation) {
        this.nzyme = nzyme;
        this.segmentation = segmentation;
    }

    // Only what the readable taps recorded is in the matrix. Findings and policies are not cut down to them.
    public Matrix matrix(UUID organizationId, UUID tenantId, TimeRange timeRange, List<UUID> readableTaps) {
        MembershipResolver resolver = segmentation.resolver(organizationId, tenantId);
        List<Policy> policies = segmentation.policies().findAll(organizationId, tenantId);

        Map<String, Map<String, long[]>> services = Maps.newLinkedHashMap();
        Map<String, Set<UUID>> taps = Maps.newHashMap();
        Map<String, long[]> continued = Maps.newHashMap();
        Map<String, DateTime[]> seen = Maps.newHashMap();

        for (Flows.Crossing crossing : segmentation.flows().crossings(organizationId, tenantId, timeRange,
                readableTaps)) {
            // A segment that was deleted and is not cleaned out of the rollup yet.
            if (crossing.sourceGroup() > 0 && resolver.segment(crossing.sourceGroup()) == null
                    || crossing.destinationGroup() > 0 && resolver.segment(crossing.destinationGroup()) == null) {
                continue;
            }

            String cell = crossing.sourceGroup() + "|" + crossing.destinationGroup();
            String service = crossing.l4Type() + "|" + crossing.port() + "|" + crossing.protocol();

            long[] counters = services.computeIfAbsent(cell, k -> Maps.newLinkedHashMap())
                    .computeIfAbsent(service, k -> new long[Outcome.values().length]);
            counters[crossing.outcome().ordinal()] += crossing.sessions() + crossing.continued();

            taps.computeIfAbsent(cell, k -> Sets.newLinkedHashSet()).addAll(crossing.taps());
            continued.computeIfAbsent(cell, k -> new long[1])[0] += crossing.continued();

            DateTime[] range = seen.computeIfAbsent(cell, k -> new DateTime[]{crossing.firstSeen(), crossing.lastSeen()});
            if (crossing.firstSeen().isBefore(range[0])) {
                range[0] = crossing.firstSeen();
            }
            if (crossing.lastSeen().isAfter(range[1])) {
                range[1] = crossing.lastSeen();
            }
        }

        boolean[] findingsLimited = new boolean[]{false};
        Map<String, long[]> findings = findingsPerCell(organizationId, tenantId, timeRange, resolver, findingsLimited);

        Set<Long> groups = Sets.newHashSet();
        List<Cell> cells = Lists.newArrayList();
        for (Map.Entry<String, Map<String, long[]>> entry : services.entrySet()) {
            String[] pair = entry.getKey().split("\\|");
            long sourceGroup = Long.parseLong(pair[0]);
            long destinationGroup = Long.parseLong(pair[1]);
            groups.add(sourceGroup);
            groups.add(destinationGroup);

            long connected = 0;
            long rejected = 0;
            long noReply = 0;
            long sent = 0;
            List<Service> cellServices = Lists.newArrayList();
            for (Map.Entry<String, long[]> service : entry.getValue().entrySet()) {
                String[] parts = service.getKey().split("\\|", -1);
                long[] c = service.getValue();
                connected += c[Outcome.CONNECTED.ordinal()];
                rejected += c[Outcome.REJECTED.ordinal()];
                noReply += c[Outcome.NO_REPLY.ordinal()];
                sent += c[Outcome.SENT.ordinal()];
                cellServices.add(new Service(parts[0], Integer.parseInt(parts[1]), parts[2],
                        c[Outcome.CONNECTED.ordinal()], c[Outcome.REJECTED.ordinal()], c[Outcome.NO_REPLY.ordinal()],
                        c[Outcome.SENT.ordinal()]));
            }

            cellServices.sort(Comparator.comparingLong((Service service) -> service.connected() + service.sent()).reversed()
                    .thenComparing(Comparator.comparingLong(Service::total).reversed()));
            int serviceCount = cellServices.size();
            if (cellServices.size() > SERVICES_PER_CELL) {
                cellServices = Lists.newArrayList(cellServices.subList(0, SERVICES_PER_CELL));
            }

            List<UUID> covering = Lists.newArrayList();
            Coverage coverage = coverage(policies, resolver, sourceGroup, destinationGroup, covering);
            long[] found = findings.getOrDefault(entry.getKey(), new long[2]);
            DateTime[] range = seen.get(entry.getKey());

            cells.add(new Cell(sourceGroup, destinationGroup, connected, rejected, noReply, sent,
                    continued.get(entry.getKey())[0], cellServices, serviceCount,
                    Lists.newArrayList(taps.get(entry.getKey())), range[0], range[1], coverage, covering,
                    found[0], found[1]));
        }

        for (Segment segment : resolver.segments()) {
            groups.add(segment.id());
        }

        List<Long> ordered = Lists.newArrayList(groups);
        ordered.sort(Comparator.comparingInt((Long group) -> rank(group, resolver))
                .thenComparing(group -> group > 0 ? resolver.segment(group).name().toLowerCase() : ""));

        Set<UUID> inScope = Sets.newHashSet();
        for (Cell cell : cells) {
            inScope.addAll(cell.policies());
        }

        List<Policy> covering = Lists.newArrayList();
        for (Policy policy : policies) {
            if (inScope.contains(policy.uuid())) {
                covering.add(policy);
            }
        }

        return new Matrix(ordered, cells, segmentation.state(organizationId, tenantId).restampPending(), covering,
                findingsLimited[0]);
    }

    private static int rank(long group, MembershipResolver resolver) {
        if (group == Flow.GROUP_UNASSIGNED) {
            return 1;
        }

        if (group == Flow.GROUP_BROADCAST) {
            return 4;
        }

        if (group == Flow.GROUP_INTERNET) {
            return 3;
        }

        return resolver.segment(group).kind() == SegmentKind.INTERNAL ? 0 : 2;
    }

    private Map<String, long[]> findingsPerCell(UUID organizationId,
                                                UUID tenantId,
                                                TimeRange timeRange,
                                                MembershipResolver resolver,
                                                boolean[] limited) {
        Map<String, long[]> result = Maps.newHashMap();

        List<Finding> recent = segmentation.findings().findAll(organizationId, tenantId, timeRange, null,
                Findings.NO_FILTERS, Findings.OrderColumn.LAST_SEEN, OrderDirection.DESC, FINDINGS_FOR_MATRIX, 0);
        limited[0] = recent.size() >= FINDINGS_FOR_MATRIX;

        for (Finding finding : recent) {
            UUID tap = finding.tapUuids().isEmpty() ? new UUID(0, 0) : finding.tapUuids().get(0);

            long sourceGroup;
            if (finding.sourceAddress() != null) {
                sourceGroup = Flow.group(resolver.resolve(tap, InetAddresses.forString(finding.sourceAddress()),
                        finding.sourceInternal(), finding.sourceAsn(), finding.sourceCountryCode()));
            } else if (!finding.sourceAddresses().isEmpty()) {
                sourceGroup = Flow.group(resolver.resolve(tap, InetAddresses.forString(finding.sourceAddresses().get(0)),
                        finding.sourceInternal(), finding.sourceAsn(), finding.sourceCountryCode()));
            } else {
                sourceGroup = finding.sourceInternal() ? Flow.GROUP_UNASSIGNED : Flow.GROUP_INTERNET;
            }

            Set<Long> destinationGroups = Sets.newHashSet();
            for (Finding.Target target : finding.targets()) {
                if (target.lastSeen() < timeRange.from().getMillis()) {
                    continue;
                }

                destinationGroups.add(Flow.group(Findings.destination(resolver, tap, target)));
            }

            for (long destinationGroup : destinationGroups) {
                long[] counters = result.computeIfAbsent(sourceGroup + "|" + destinationGroup, k -> new long[2]);
                counters[finding.findingClass() == FindingClass.CROSSED ? 0 : 1]++;
            }
        }

        return result;
    }

    private static Coverage coverage(List<Policy> policies,
                                     MembershipResolver resolver,
                                     long sourceGroup,
                                     long destinationGroup,
                                     List<UUID> covering) {
        Coverage best = Coverage.NONE;

        for (Policy policy : policies) {
            if (policy.status() == PolicyStatus.PAUSED) {
                continue;
            }

            Coverage sources = covers(policy.scope().sources(), sourceGroup, resolver);
            Coverage destinations = destinationGroup == Flow.GROUP_BROADCAST
                    ? coversBroadcast(policy.scope().destinations())
                    : covers(policy.scope().destinations(), destinationGroup, resolver);
            if (sources == Coverage.NONE || destinations == Coverage.NONE) {
                continue;
            }

            Coverage coverage = sources == Coverage.FULL && destinations == Coverage.FULL
                    && policy.scope().services().isEmpty() ? Coverage.FULL : Coverage.PARTIAL;

            covering.add(policy.uuid());
            if (coverage.ordinal() > best.ordinal()) {
                best = coverage;
            }
        }

        return best;
    }

    private static Coverage covers(List<PolicyRule.Selector> selectors, long group, MembershipResolver resolver) {
        if (selectors.isEmpty()) {
            return Coverage.FULL;
        }

        Segment segment = group > 0 ? resolver.segment(group) : null;
        boolean internal = segment == null ? group == Flow.GROUP_UNASSIGNED : segment.kind() == SegmentKind.INTERNAL;

        Coverage best = Coverage.NONE;
        for (PolicyRule.Selector selector : selectors) {
            Coverage coverage = Coverage.NONE;

            switch (selector.type()) {
                case ANY:
                    coverage = Coverage.FULL;
                    break;
                case INTERNAL:
                    coverage = internal ? Coverage.FULL : Coverage.NONE;
                    break;
                case EXTERNAL:
                    coverage = internal ? Coverage.NONE : Coverage.FULL;
                    break;
                case UNASSIGNED:
                    coverage = group == Flow.GROUP_UNASSIGNED ? Coverage.FULL : Coverage.NONE;
                    break;
                case SEGMENT:
                    coverage = segment != null && segment.uuid().toString().equalsIgnoreCase(selector.value())
                            ? Coverage.FULL : Coverage.NONE;
                    break;
                case TAG:
                case ASSET:
                    if (internal) {
                        UUID uuid = UUID.fromString(selector.value());
                        List<InetAddress> addresses = selector.type() == PolicyRule.SelectorType.TAG
                                ? resolver.addressesOfTag(uuid) : resolver.addressesOfAsset(uuid);
                        for (InetAddress address : addresses) {
                            Segment of = resolver.segmentOfAnyTap(address);
                            if (of == null ? group == Flow.GROUP_UNASSIGNED : of.id() == group) {
                                coverage = Coverage.PARTIAL;
                                break;
                            }
                        }
                    }
                    break;
                case ADDRESS: {
                    CIDR network = CIDR.parse(selector.value());
                    coverage = PolicyMatcher.isBroadcastRange(network)
                            ? Coverage.NONE : coversNetwork(network, group, segment, resolver);
                    break;
                }
                case ASN:
                    if (!internal) {
                        coverage = Coverage.PARTIAL;
                        if (segment != null) {
                            coverage = Coverage.NONE;
                            for (Segment.Member member : segment.members()) {
                                if (member.asn() != null && String.valueOf(member.asn()).equals(selector.value())) {
                                    coverage = Coverage.PARTIAL;
                                }
                            }
                        }
                    }
                    break;
                case COUNTRY:
                    coverage = internal ? Coverage.NONE : Coverage.PARTIAL;
                    break;
                default:
                    break;
            }

            if (coverage.ordinal() > best.ordinal()) {
                best = coverage;
            }
        }

        return best;
    }

    // A scope only takes in broadcast and multicast addresses by naming them: all of them, or some by address.
    private static Coverage coversBroadcast(List<PolicyRule.Selector> selectors) {
        Coverage best = Coverage.NONE;
        for (PolicyRule.Selector selector : selectors) {
            if (selector.type() == PolicyRule.SelectorType.BROADCAST) {
                return Coverage.FULL;
            }

            if (selector.type() == PolicyRule.SelectorType.ADDRESS
                    && PolicyMatcher.isBroadcastRange(CIDR.parse(selector.value()))) {
                best = Coverage.PARTIAL;
            }
        }

        return best;
    }

    private static Coverage coversNetwork(CIDR network, long group, @Nullable Segment segment, MembershipResolver resolver) {
        if (segment == null) {
            Segment of = resolver.segmentOfAnyTap(network.address());
            if (of != null) {
                return Coverage.NONE;
            }

            boolean internal = resolver.siteLocal(network.address());
            return internal == (group == Flow.GROUP_UNASSIGNED) ? Coverage.PARTIAL : Coverage.NONE;
        }

        boolean any = false;
        boolean all = true;
        boolean hasNetworks = false;
        for (Segment.Member member : segment.members()) {
            if (member.network() == null) {
                all = false;
                continue;
            }

            hasNetworks = true;
            boolean contains = network.prefix() <= member.network().prefix() && network.contains(member.network().address());
            boolean contained = member.network().contains(network.address());

            if (contains || contained) {
                any = true;
            }

            if (!contains) {
                all = false;
            }
        }

        if (!any) {
            return Coverage.NONE;
        }

        return all && hasNetworks ? Coverage.FULL : Coverage.PARTIAL;
    }

    public Flows.Page flowsBetween(UUID organizationId,
                                   UUID tenantId,
                                   TimeRange timeRange,
                                   List<UUID> taps,
                                   long sourceGroup,
                                   long destinationGroup,
                                   Filters filters,
                                   Flows.OrderColumn orderColumn,
                                   OrderDirection orderDirection,
                                   int limit,
                                   int offset) {
        MembershipResolver resolver = segmentation.resolver(organizationId, tenantId);

        Map<String, Object> bindings = Maps.newHashMap();
        String sql = "AND " + groupCondition("source", sourceGroup, bindings)
                + " AND " + groupCondition("destination", destinationGroup, bindings) + " ";

        return segmentation.flows().page(organizationId, tenantId, timeRange, taps, new Flows.Prefilter(sql, bindings),
                filters, orderColumn, orderDirection, limit, offset, resolver);
    }

    public Flows.AddressPage addressesOf(UUID organizationId,
                                         UUID tenantId,
                                         TimeRange timeRange,
                                         List<UUID> taps,
                                         long group,
                                         Filters filters,
                                         Flows.AddressOrderColumn orderColumn,
                                         OrderDirection orderDirection,
                                         int limit,
                                         int offset) {
        Map<String, Object> sourceBindings = Maps.newHashMap();
        Map<String, Object> destinationBindings = Maps.newHashMap();

        return segmentation.flows().addresses(organizationId, tenantId, timeRange, taps,
                new Flows.Prefilter("AND " + groupCondition("source", group, sourceBindings) + " ", sourceBindings),
                new Flows.Prefilter("AND " + groupCondition("destination", group, destinationBindings) + " ",
                        destinationBindings),
                filters, orderColumn, orderDirection, limit, offset);
    }

    private static String groupCondition(String side, long group, Map<String, Object> bindings) {
        if (group == Flow.GROUP_UNASSIGNED) {
            return "(" + side + "_internal AND " + side + "_segment_id IS NULL)";
        }

        if (group == Flow.GROUP_BROADCAST) {
            return side + "_key LIKE '" + Flows.BROADCAST_KEYS + "'";
        }

        if (group == Flow.GROUP_INTERNET) {
            return "(NOT " + side + "_internal AND " + side + "_segment_id IS NULL AND " + side + "_key NOT LIKE '"
                    + Flows.BROADCAST_KEYS + "')";
        }

        bindings.put(side + "_group", group);
        return side + "_segment_id = :" + side + "_group";
    }

    public static List<Flow> mergeTaps(List<Flow> flows) {
        Map<String, Flow> merged = Maps.newLinkedHashMap();
        for (Flow flow : flows) {
            String key = flow.sourceGroup() + "/" + flow.sourceKey() + "|" + flow.destinationGroup() + "/"
                    + flow.destinationKey() + "|" + flow.l4Type() + "|" + flow.port() + "|" + flow.protocol() + "|"
                    + flow.outcome();

            merged.merge(key, flow, (a, b) -> {
                Flow most = a.sessions() + a.continued() >= b.sessions() + b.continued() ? a : b;
                return most.withSessions(
                        most.sessions(),
                        most.continued(),
                        a.firstSeen().isBefore(b.firstSeen()) ? a.firstSeen() : b.firstSeen(),
                        a.lastSeen().isAfter(b.lastSeen()) ? a.lastSeen() : b.lastSeen()
                );
            });
        }

        List<Flow> result = Lists.newArrayList(merged.values());
        result.sort(Comparator.comparingLong((Flow f) -> f.sessions() + f.continued()).reversed());

        return result;
    }

    public List<PolicyOverview> policies(UUID organizationId, UUID tenantId, DateTime since) {
        MembershipResolver resolver = segmentation.resolver(organizationId, tenantId);
        Map<Long, Findings.PolicySummary> summaries = segmentation.findings().summarize(organizationId, tenantId, since);
        boolean stale = segmentation.state(organizationId, tenantId).restampPending();
        Map<Long, Long> findings = segmentation.findings().countPerPolicy(organizationId, tenantId);

        List<PolicyOverview> result = Lists.newArrayList();
        for (Policy policy : segmentation.policies().findAll(organizationId, tenantId)) {
            Findings.PolicySummary summary = summaries.getOrDefault(policy.id(), Findings.PolicySummary.empty());
            result.add(new PolicyOverview(policy, state(policy, summary, resolver, since, stale), summary,
                    atFindingsLimit(findings, policy)));
        }

        return result;
    }

    public List<PolicyOverview> policies(UUID organizationId,
                                         UUID tenantId,
                                         DateTime since,
                                         Filters filters,
                                         Policies.OrderColumn orderColumn,
                                         OrderDirection orderDirection,
                                         int limit,
                                         int offset) {
        MembershipResolver resolver = segmentation.resolver(organizationId, tenantId);
        boolean stale = segmentation.state(organizationId, tenantId).restampPending();
        Map<Long, Long> findings = segmentation.findings().countPerPolicy(organizationId, tenantId);

        List<PolicyOverview> result = Lists.newArrayList();
        for (Policies.Listed listed : segmentation.policies().findAll(filters, orderColumn, orderDirection, limit,
                offset, organizationId, tenantId, since)) {
            result.add(new PolicyOverview(listed.policy(),
                    state(listed.policy(), listed.summary(), resolver, since, stale), listed.summary(),
                    atFindingsLimit(findings, listed.policy())));
        }

        return result;
    }

    public PolicyOverview policy(Policy policy, DateTime since) {
        MembershipResolver resolver = segmentation.resolver(policy.organizationId(), policy.tenantId());
        Findings.PolicySummary summary = segmentation.findings()
                .summarize(policy.organizationId(), policy.tenantId(), since)
                .getOrDefault(policy.id(), Findings.PolicySummary.empty());
        boolean stale = segmentation.state(policy.organizationId(), policy.tenantId()).restampPending();

        return new PolicyOverview(policy, state(policy, summary, resolver, since, stale), summary,
                atFindingsLimit(segmentation.findings().countPerPolicy(policy.organizationId(), policy.tenantId()),
                        policy));
    }

    private static boolean atFindingsLimit(Map<Long, Long> findings, Policy policy) {
        return findings.getOrDefault(policy.id(), 0L) >= Findings.MAX_FINDINGS_PER_POLICY;
    }

    private PolicyState state(Policy policy,
                              Findings.PolicySummary summary,
                              MembershipResolver resolver,
                              DateTime since,
                              boolean stale) {
        if (policy.status() == PolicyStatus.PAUSED) {
            return PolicyState.PAUSED;
        }

        if (summary.crossedFindings() > 0) {
            return PolicyState.CROSSED;
        }

        if (summary.attemptedFindings() > 0) {
            return PolicyState.ATTEMPTED;
        }

        return observable(policy, resolver, since, stale) ? PolicyState.HOLDING : PolicyState.NOT_OBSERVABLE;
    }

    // Assumed if taps recorded traffic of the sources of the scope and of its destinations. It is an approximation: a
    // tap that sees a host talk to the Internet does not have to see it talk to its neighbor.
    private boolean observable(Policy policy, MembershipResolver resolver, DateTime since, boolean stale) {
        return sideObserved(policy, policy.scope().sources(), "source", "destination", resolver, since, stale)
                && sideObserved(policy, policy.scope().destinations(), "destination", "source", resolver, since, stale);
    }

    private boolean sideObserved(Policy policy,
                                 List<PolicyRule.Selector> selectors,
                                 String side,
                                 String otherSide,
                                 MembershipResolver resolver,
                                 DateTime since,
                                 boolean stale) {
        Map<String, Object> bindings = Maps.newHashMap();
        String condition = observedCondition(selectors, side, otherSide, resolver, bindings, stale);

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT EXISTS(SELECT 1 FROM segmentation_flows " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND bucket >= date_trunc('hour', :since::timestamptz, 'UTC') " +
                                (condition == null ? "" : "AND " + condition) + ")")
                        .bind("organization_id", policy.organizationId())
                        .bind("tenant_id", policy.tenantId())
                        .bind("since", since)
                        .bindMap(bindings)
                        .mapTo(Boolean.class)
                        .one()
        );
    }

    public Preview preview(UUID organizationId,
                           UUID tenantId,
                           PolicyRule scope,
                           List<PolicyRule> allowed,
                           TimeRange timeRange,
                           List<UUID> taps) {
        List<Policy> existing = segmentation.policies().findAll(organizationId, tenantId);
        MembershipResolver resolver = nzyme.getDatabase().withHandle(handle ->
                segmentation.buildResolver(handle, organizationId, tenantId, existing,
                        Policies.referencedAssets(scope, allowed)));
        boolean stale = segmentation.state(organizationId, tenantId).restampPending();

        Map<String, Object> bindings = Maps.newHashMap();
        StringBuilder sql = new StringBuilder();
        String sources = sideCondition(scope.sources(), "source", resolver, bindings, stale);
        if (sources != null) {
            sql.append("AND ").append(sources).append(" ");
        }
        String destinations = sideCondition(scope.destinations(), "destination", resolver, bindings, stale);
        if (destinations != null) {
            sql.append("AND ").append(destinations).append(" ");
        }

        boolean namesBroadcast = scope.destinations().stream().anyMatch(selector ->
                selector.type() == PolicyRule.SelectorType.BROADCAST || selector.type() == PolicyRule.SelectorType.ADDRESS);
        if (!namesBroadcast) {
            sql.append("AND destination_key NOT LIKE '").append(Flows.BROADCAST_KEYS).append("' ");
        }

        List<Flow> read = segmentation.flows().read(organizationId, tenantId, timeRange, taps,
                new Flows.Prefilter(sql.toString(), bindings), resolver, PREVIEW_FLOW_LIMIT);
        boolean truncated = read.size() >= PREVIEW_FLOW_LIMIT;

        PolicyMatcher matcher = new PolicyMatcher(null, scope, allowed);

        class Row {
            PreviewSide source;
            PreviewSide destination;
            String l4Type;
            int port;
            String protocol;
            PolicyMatcher.Verdict verdict;
            final long[] outcomes = new long[Outcome.values().length];
            final Set<String> sourceHosts = Sets.newLinkedHashSet();
            final Set<String> destinationHosts = Sets.newLinkedHashSet();
            DateTime firstSeen;
            DateTime lastSeen;
        }

        Map<String, Row> rows = Maps.newLinkedHashMap();
        Set<Integer> usedAllowed = Sets.newHashSet();
        long allowedSessions = 0;
        long crossedSessions = 0;
        long attemptedSessions = 0;
        long indeterminateSessions = 0;

        for (Flow flow : mergeTaps(read)) {
            PolicyMatcher.Verdict verdict = matcher.evaluate(flow.source(), flow.destination(), flow.l4Type(),
                    flow.port(), flow.protocol());
            if (verdict == PolicyMatcher.Verdict.OUT_OF_SCOPE) {
                continue;
            }

            long sessions = flow.sessions() + flow.continued();
            switch (verdict) {
                case ALLOWED:
                    allowedSessions += sessions;
                    usedAllowed.add(matcher.allowedBy(flow.source(), flow.destination(), flow.l4Type(), flow.port(),
                            flow.protocol()));
                    break;
                case VIOLATION:
                    if (flow.outcome().findingClass() == FindingClass.CROSSED) {
                        crossedSessions += sessions;
                    } else {
                        attemptedSessions += sessions;
                    }
                    break;
                default:
                    indeterminateSessions += sessions;
            }

            PreviewSide source = side(flow.source());
            PreviewSide destination = side(flow.destination());
            String key = source.selector() + "|" + destination.selector() + "|" + flow.l4Type() + "|" + flow.port()
                    + "|" + flow.protocol() + "|" + verdict;

            Row row = rows.computeIfAbsent(key, k -> {
                Row created = new Row();
                created.source = source;
                created.destination = destination;
                created.l4Type = flow.l4Type();
                created.port = flow.port();
                created.protocol = flow.protocol();
                created.verdict = verdict;
                created.firstSeen = flow.firstSeen();
                created.lastSeen = flow.lastSeen();
                return created;
            });

            row.outcomes[flow.outcome().ordinal()] += sessions;
            if (flow.source().hasAddress()) {
                row.sourceHosts.add(InetAddresses.toAddrString(flow.source().address()));
            }
            if (flow.destination().hasAddress()) {
                row.destinationHosts.add(InetAddresses.toAddrString(flow.destination().address()));
            }
            if (flow.firstSeen().isBefore(row.firstSeen)) {
                row.firstSeen = flow.firstSeen();
            }
            if (flow.lastSeen().isAfter(row.lastSeen)) {
                row.lastSeen = flow.lastSeen();
            }
        }

        List<PreviewRow> result = Lists.newArrayList();
        for (Row row : rows.values()) {
            result.add(new PreviewRow(row.source, row.destination, row.l4Type, row.port, row.protocol, row.verdict,
                    row.outcomes[Outcome.CONNECTED.ordinal()], row.outcomes[Outcome.REJECTED.ordinal()],
                    row.outcomes[Outcome.NO_REPLY.ordinal()], row.outcomes[Outcome.SENT.ordinal()],
                    row.sourceHosts.size(), row.destinationHosts.size(),
                    sample(row.sourceHosts), sample(row.destinationHosts), row.firstSeen, row.lastSeen));
        }

        result.sort(Comparator.comparingLong(PreviewRow::total).reversed());
        if (result.size() > PREVIEW_ROW_LIMIT) {
            result = Lists.newArrayList(result.subList(0, PREVIEW_ROW_LIMIT));
            truncated = true;
        }

        // With a cut off read, a crossing that looks unused may just not have been among the busiest flows.
        List<Integer> unusedAllowed = Lists.newArrayList();
        if (!truncated) {
            for (int i = 0; i < allowed.size(); i++) {
                if (!usedAllowed.contains(i)) {
                    unusedAllowed.add(i);
                }
            }
        }

        return new Preview(result, allowedSessions, crossedSessions, attemptedSessions, indeterminateSessions,
                unusedAllowed, truncated);
    }

    private static List<String> sample(Collection<String> hosts) {
        List<String> sample = Lists.newArrayList();
        for (String host : hosts) {
            if (sample.size() >= SAMPLE_HOSTS) {
                break;
            }
            sample.add(host);
        }

        return sample;
    }

    public static PreviewSide side(Endpoint endpoint) {
        if (endpoint.broadcast()) {
            return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.BROADCAST, null), BROADCAST_NAME);
        }

        if (endpoint.segment() != null) {
            return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.SEGMENT,
                    endpoint.segment().uuid().toString()), endpoint.segment().name());
        }

        if (endpoint.internal()) {
            return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.UNASSIGNED, null), "Unassigned");
        }

        if (endpoint.asn() != null) {
            return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.ASN, String.valueOf(endpoint.asn())),
                    "AS" + endpoint.asn());
        }

        if (endpoint.countryCode() != null) {
            return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.COUNTRY,
                    endpoint.countryCode().toUpperCase()), endpoint.countryCode().toUpperCase());
        }

        return new PreviewSide(new PolicyRule.Selector(PolicyRule.SelectorType.EXTERNAL, null), "Internet");
    }

    /*
     * NULL if any flow shows that taps see the side. An answered flow shows both of its sides, because a tap saw both
     * directions of it. A flow that was not answered does not show its destination: one that does not exist can be
     * sent to as well.
     */
    @Nullable
    static String observedCondition(List<PolicyRule.Selector> selectors,
                                    String side,
                                    String otherSide,
                                    MembershipResolver resolver,
                                    Map<String, Object> bindings,
                                    boolean stale) {
        String condition = sideCondition(selectors, side, resolver, bindings, stale, true);
        if (condition == null) {
            return null;
        }

        String answered = sideCondition(selectors, otherSide, resolver, bindings, stale, true);
        if (answered == null) {
            return null;
        }

        return "(" + condition + " OR (" + answered + " AND outcome = '" + Outcome.CONNECTED.name() + "'))";
    }

    @Nullable
    private static String sideCondition(List<PolicyRule.Selector> selectors,
                                        String side,
                                        MembershipResolver resolver,
                                        Map<String, Object> bindings,
                                        boolean stale) {
        return sideCondition(selectors, side, resolver, bindings, stale, false);
    }

    // The addresses sessions are stored as site-local for.
    private static final List<CIDR> PRIVATE_NETWORKS = List.of(CIDR.parse("10.0.0.0/8"), CIDR.parse("172.16.0.0/12"),
            CIDR.parse("192.168.0.0/16"), CIDR.parse("fec0::/10"));

    static boolean canBeExternal(CIDR network, Collection<Segment> segments, InternalAddresses.Rules internalAddresses) {
        boolean insideSegment = false;

        for (Segment segment : segments) {
            for (Segment.Member member : segment.members()) {
                if (member.network() == null) {
                    continue;
                }

                if (segment.kind() == SegmentKind.EXTERNAL) {
                    if (isWithin(member.network(), network) || isWithin(network, member.network())) {
                        return true;
                    }
                } else if (isWithin(network, member.network())) {
                    insideSegment = true;
                }
            }
        }

        if (insideSegment) {
            return false;
        }

        if (internalAddresses.cgnatIsInternal() && isWithin(network, InternalAddresses.CGNAT_NETWORK)) {
            return false;
        }

        return PRIVATE_NETWORKS.stream().noneMatch(privateNetwork -> isWithin(network, privateNetwork));
    }

    private static boolean isWithin(CIDR inner, CIDR outer) {
        return outer.prefix() <= inner.prefix() && outer.contains(inner.address());
    }

    /*
     * NULL if the selectors cannot narrow the read down. The condition only has to be a superset: the exact decision
     * is made by the matcher. With exact set, it only takes what is known to match, and external sides, which never
     * have an address, for the networks that can be external.
     */
    @Nullable
    private static String sideCondition(List<PolicyRule.Selector> selectors,
                                        String side,
                                        MembershipResolver resolver,
                                        Map<String, Object> bindings,
                                        boolean stale,
                                        boolean exact) {
        if (selectors.isEmpty()) {
            return null;
        }

        List<String> conditions = Lists.newArrayList();
        int i = 0;
        for (PolicyRule.Selector selector : selectors) {
            String bind = side + "_selector_" + i++;

            switch (selector.type()) {
                case ANY:
                    return null;
                case INTERNAL:
                    conditions.add(side + "_internal");
                    break;
                case EXTERNAL:
                    // Broadcast and multicast destinations are stored as not internal, and are not external either.
                    conditions.add("(NOT " + side + "_internal AND " + side + "_key NOT LIKE '" + Flows.BROADCAST_KEYS + "')");
                    break;
                case UNASSIGNED:
                    if (stale) {
                        return null;
                    }
                    conditions.add("(" + side + "_internal AND " + side + "_segment_id IS NULL)");
                    break;
                case BROADCAST:
                    conditions.add(side + "_key LIKE '" + Flows.BROADCAST_KEYS + "'");
                    break;
                case SEGMENT: {
                    if (stale) {
                        return null;
                    }
                    Segment segment = resolver.segment(UUID.fromString(selector.value()));
                    if (segment != null) {
                        bindings.put(bind, segment.id());
                        conditions.add(side + "_segment_id = :" + bind);
                    }
                    break;
                }
                case TAG:
                case ASSET: {
                    UUID uuid = UUID.fromString(selector.value());
                    List<InetAddress> addresses = selector.type() == PolicyRule.SelectorType.TAG
                            ? resolver.addressesOfTag(uuid) : resolver.addressesOfAsset(uuid);

                    StringBuilder literal = new StringBuilder("{");
                    for (InetAddress address : addresses) {
                        if (literal.length() > 1) {
                            literal.append(",");
                        }
                        literal.append(InetAddresses.toAddrString(address));
                    }
                    literal.append("}");

                    bindings.put(bind, literal.toString());
                    // Sides that were folded have no address and cannot be ruled out.
                    conditions.add(exact ? side + "_address = ANY(:" + bind + "::inet[])"
                            : "(" + side + "_address = ANY(:" + bind + "::inet[]) OR (" + side
                            + "_address IS NULL AND " + side + "_internal))");
                    break;
                }
                case ADDRESS: {
                    CIDR network = CIDR.parse(selector.value());
                    bindings.put(bind, network.toString());

                    if (!exact) {
                        conditions.add("(" + side + "_address <<= :" + bind + "::inet OR " + side + "_address IS NULL)");
                    } else if (canBeExternal(network, resolver.segments(), resolver.internalAddresses())) {
                        conditions.add("(" + side + "_address <<= :" + bind + "::inet OR (" + side
                                + "_address IS NULL AND NOT " + side + "_internal AND " + side + "_key NOT LIKE '"
                                + Flows.BROADCAST_KEYS + "'))");
                    } else {
                        conditions.add(side + "_address <<= :" + bind + "::inet");
                    }
                    break;
                }
                case ASN:
                    bindings.put(bind, Long.parseLong(selector.value()));
                    conditions.add(side + "_asn = :" + bind);
                    break;
                case COUNTRY:
                    bindings.put(bind, selector.value().toUpperCase());
                    conditions.add(side + "_country_code = :" + bind);
                    break;
                default:
                    return null;
            }
        }

        if (conditions.isEmpty()) {
            // Only selectors that match nothing, like a segment that is gone.
            return "FALSE";
        }

        return "(" + String.join(" OR ", conditions) + ")";
    }

}
