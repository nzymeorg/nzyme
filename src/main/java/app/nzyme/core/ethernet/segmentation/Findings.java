package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelJson;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.segmentation.filters.FindingFilters;
import app.nzyme.core.ethernet.segmentation.filters.FindingTargetFilters;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Findings {

    private static final Logger LOG = LogManager.getLogger(Findings.class);

    public static final int MAX_TARGETS = 100;
    public static final int MAX_SOURCE_ADDRESSES = 20;

    private static final int RECONCILE_BATCH_SIZE = 500;

    // Spoofed sources could otherwise open findings without end.
    public static final int MAX_FINDINGS_PER_POLICY = 10_000;

    public record Violation(Policy policy,
                            FindingClass findingClass,
                            String sourceKey,
                            @Nullable String sourceAddress,
                            boolean sourceInternal,
                            @Nullable Long sourceAsn,
                            @Nullable String sourceCountryCode,
                            Set<String> sourceAddresses,
                            List<Finding.Target> targets,
                            Set<UUID> taps,
                            DateTime firstSeen,
                            DateTime lastSeen) {

        public long sessions() {
            long sessions = 0;
            for (Finding.Target target : targets) {
                sessions += target.sessions();
            }

            return sessions;
        }

    }

    public record PolicySummary(long crossedFindings,
                                long attemptedFindings,
                                @Nullable DateTime lastCrossed,
                                @Nullable DateTime lastAttempted) {

        public static PolicySummary empty() {
            return new PolicySummary(0, 0, null, null);
        }

    }

    private static final String SELECT = "SELECT f.*, host(f.source_address) AS source_address_host, " +
            "p.uuid AS policy_uuid, p.name AS policy_name FROM segmentation_findings AS f " +
            "JOIN segmentation_policies AS p ON p.id = f.policy_id ";

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    public Findings(NzymeNode nzyme, Segmentation segmentation) {
        this.nzyme = nzyme;
        this.segmentation = segmentation;
    }

    public enum OrderColumn {

        LAST_SEEN("f.last_seen"),
        FIRST_SEEN("f.first_seen"),
        POLICY_NAME("lower(p.name)"),
        FINDING_CLASS("f.finding_class"),
        SOURCE("f.source_address"),
        SESSION_COUNT("f.session_count"),
        TARGET_COUNT("f.target_count");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public enum TargetOrderColumn {

        LAST_SEEN("t.last_seen"),
        FIRST_SEEN("t.first_seen"),
        ADDRESS("t.address"),
        PORT("t.port"),
        OUTCOME("t.outcome"),
        SESSIONS("t.sessions");

        private final String columnName;

        TargetOrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public static final Filters NO_FILTERS = Filters.create(Collections.emptyMap());

    private static final String IN_RANGE = "WHERE f.organization_id = :organization_id AND f.tenant_id = :tenant_id " +
            "AND f.first_seen <= :tr_to AND f.last_seen >= :tr_from AND (:policy::uuid IS NULL OR p.uuid = :policy::uuid) ";

    private static final String TARGETS = "FROM segmentation_findings AS f, jsonb_to_recordset(f.targets) AS " +
            "t(address inet, internal boolean, asn bigint, country_code text, l4_type text, port int, " +
            "protocol text, outcome text, sessions bigint, first_seen bigint, last_seen bigint) WHERE f.id = :id ";

    public long count(UUID organizationId,
                      UUID tenantId,
                      TimeRange timeRange,
                      @Nullable UUID policy,
                      Filters filters) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new FindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM segmentation_findings AS f " +
                                "JOIN segmentation_policies AS p ON p.id = f.policy_id " + IN_RANGE +
                                filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("policy", policy == null ? null : policy.toString())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Finding> findAll(UUID organizationId,
                                 UUID tenantId,
                                 TimeRange timeRange,
                                 @Nullable UUID policy,
                                 Filters filters,
                                 OrderColumn orderColumn,
                                 OrderDirection orderDirection,
                                 int limit,
                                 int offset) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new FindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT + IN_RANGE + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, f.source_key ASC, f.id DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("policy", policy == null ? null : policy.toString())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> map(rs))
                        .list()
        );
    }

    public long countTargets(Finding finding, Filters filters) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new FindingTargetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) " + TARGETS + filterFragment.whereSql())
                        .bind("id", finding.id())
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Finding.Target> findTargets(Finding finding,
                                            Filters filters,
                                            TargetOrderColumn orderColumn,
                                            OrderDirection orderDirection,
                                            int limit,
                                            int offset) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new FindingTargetFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT host(t.address) AS address, t.internal, t.asn, t.country_code, " +
                                "t.l4_type, t.port, t.protocol, t.outcome, t.sessions, t.first_seen, t.last_seen " +
                                TARGETS + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, t.address ASC, t.port ASC, " +
                                "t.outcome ASC LIMIT :limit OFFSET :offset")
                        .bind("id", finding.id())
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> {
                            long asn = rs.getLong("asn");
                            Long asnOrNull = rs.wasNull() ? null : asn;

                            return new Finding.Target(
                                    rs.getString("address"),
                                    rs.getBoolean("internal"),
                                    asnOrNull,
                                    rs.getString("country_code"),
                                    rs.getString("l4_type"),
                                    rs.getInt("port"),
                                    rs.getString("protocol"),
                                    Outcome.valueOf(rs.getString("outcome")),
                                    rs.getLong("sessions"),
                                    rs.getLong("first_seen"),
                                    rs.getLong("last_seen")
                            );
                        })
                        .list()
        );
    }

    public Optional<Finding> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery(SELECT + "WHERE f.uuid = :uuid AND f.organization_id = :organization_id " +
                                "AND f.tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> map(rs))
                        .findOne()
        );
    }

    public void delete(Finding finding) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM segmentation_findings WHERE id = :id")
                        .bind("id", finding.id())
                        .execute()
        );
    }

    public int delete(BulkSelection selection,
                      UUID organizationId,
                      UUID tenantId,
                      TimeRange timeRange,
                      @Nullable UUID policy) {
        if (selection.isEmpty()) {
            return 0;
        }

        if (selection.isChecked()) {
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createUpdate("DELETE FROM segmentation_findings WHERE organization_id = :organization_id " +
                                    "AND tenant_id = :tenant_id AND uuid IN (<uuids>)")
                            .bind("organization_id", organizationId)
                            .bind("tenant_id", tenantId)
                            .bindList("uuids", selection.uuids())
                            .execute()
            );
        }

        FilterSqlFragment filterFragment = FilterSql.generate(selection.filters(), new FindingFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createUpdate("DELETE FROM segmentation_findings WHERE id IN (SELECT f.id " +
                                "FROM segmentation_findings AS f JOIN segmentation_policies AS p ON p.id = f.policy_id " +
                                IN_RANGE + filterFragment.whereSql() + ")")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("policy", policy == null ? null : policy.toString())
                        .bindMap(filterFragment.bindings())
                        .execute()
        );
    }

    public Map<Long, PolicySummary> summarize(UUID organizationId, UUID tenantId, DateTime since) {
        Map<Long, PolicySummary> result = Maps.newHashMap();

        nzyme.getDatabase().useHandle(handle ->
                handle.createQuery("SELECT policy_id, finding_class, COUNT(*) AS findings, " +
                                "MAX(last_seen) AS last_seen FROM segmentation_findings " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND last_seen >= :since GROUP BY policy_id, finding_class")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("since", since)
                        .map((rs, ctx) -> {
                            long policyId = rs.getLong("policy_id");
                            PolicySummary existing = result.getOrDefault(policyId, PolicySummary.empty());
                            DateTime lastSeen = new DateTime(rs.getTimestamp("last_seen"));

                            if (FindingClass.valueOf(rs.getString("finding_class")) == FindingClass.CROSSED) {
                                result.put(policyId, new PolicySummary(rs.getLong("findings"),
                                        existing.attemptedFindings(), lastSeen, existing.lastAttempted()));
                            } else {
                                result.put(policyId, new PolicySummary(existing.crossedFindings(),
                                        rs.getLong("findings"), existing.lastCrossed(), lastSeen));
                            }
                            return true;
                        })
                        .list()
        );

        return result;
    }

    public int record(Handle handle, UUID organizationId, UUID tenantId, Collection<Violation> violations) {
        int opened = 0;
        long overLimit = 0;
        Map<Long, Long> findingsOfPolicies = null;

        // In key order, so that concurrent writers can never wait on each other in a circle.
        List<Violation> ordered = Lists.newArrayList(violations);
        ordered.sort(Comparator.comparing((Violation v) -> v.policy().id())
                .thenComparing(v -> v.findingClass().name())
                .thenComparing(Violation::sourceKey));

        for (Violation violation : ordered) {
            boolean audit = violation.policy().status() == PolicyStatus.AUDIT;

            Optional<Finding> existing = handle.createQuery(SELECT + "WHERE f.policy_id = :policy_id " +
                            "AND f.finding_class = :finding_class AND f.source_key = :source_key FOR UPDATE OF f")
                    .bind("policy_id", violation.policy().id())
                    .bind("finding_class", violation.findingClass().name())
                    .bind("source_key", violation.sourceKey())
                    .map((rs, ctx) -> map(rs))
                    .findOne();

            if (existing.isEmpty()) {
                if (findingsOfPolicies == null) {
                    findingsOfPolicies = countPerPolicy(handle, organizationId, tenantId);
                }

                if (findingsOfPolicies.getOrDefault(violation.policy().id(), 0L) >= MAX_FINDINGS_PER_POLICY) {
                    overLimit++;
                    continue;
                }

                findingsOfPolicies.merge(violation.policy().id(), 1L, Long::sum);

                List<Finding.Target> targets = cap(violation.targets());

                handle.createUpdate("INSERT INTO segmentation_findings(uuid, organization_id, tenant_id, " +
                                "policy_id, finding_class, source_key, source_address, source_internal, " +
                                "source_asn, source_country_code, source_addresses, targets, target_count, " +
                                "session_count, tap_uuids, audit, first_seen, last_seen, created_at, " +
                                "updated_at) VALUES(:uuid, :organization_id, :tenant_id, :policy_id, " +
                                ":finding_class, :source_key, :source_address::inet, :source_internal, " +
                                ":source_asn, :source_country_code, :source_addresses::jsonb, :targets::jsonb, " +
                                ":target_count, :session_count, :tap_uuids::jsonb, :audit, :first_seen, " +
                                ":last_seen, NOW(), NOW())")
                        .bind("uuid", UUID.randomUUID())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("policy_id", violation.policy().id())
                        .bind("finding_class", violation.findingClass().name())
                        .bind("source_key", violation.sourceKey())
                        .bind("source_address", violation.sourceAddress())
                        .bind("source_internal", violation.sourceInternal())
                        .bind("source_asn", violation.sourceAsn())
                        .bind("source_country_code", violation.sourceCountryCode())
                        .bind("source_addresses", DnsTunnelJson.toJson(capAddresses(violation.sourceAddresses())))
                        .bind("targets", DnsTunnelJson.toJson(targets))
                        .bind("target_count", violation.targets().size())
                        .bind("session_count", violation.sessions())
                        .bind("tap_uuids", DnsTunnelJson.toJson(strings(violation.taps())))
                        .bind("audit", audit)
                        .bind("first_seen", violation.firstSeen())
                        .bind("last_seen", violation.lastSeen())
                        .execute();

                opened++;
                continue;
            }

            Finding finding = existing.get();

            Map<String, Finding.Target> merged = Maps.newLinkedHashMap();
            for (Finding.Target target : finding.targets()) {
                merged.put(target.identity(), target);
            }

            for (Finding.Target target : violation.targets()) {
                Finding.Target known = merged.get(target.identity());
                if (known == null) {
                    merged.put(target.identity(), target);
                } else {
                    merged.put(target.identity(), new Finding.Target(
                            known.address(), known.internal(), known.asn(), known.countryCode(), known.l4Type(),
                            known.port(), known.protocol(), known.outcome(), known.sessions() + target.sessions(),
                            Math.min(known.firstSeen(), target.firstSeen()), Math.max(known.lastSeen(), target.lastSeen())
                    ));
                }
            }

            Set<String> sourceAddresses = Sets.newLinkedHashSet(finding.sourceAddresses());
            sourceAddresses.addAll(violation.sourceAddresses());

            Set<UUID> taps = Sets.newLinkedHashSet(finding.tapUuids());
            taps.addAll(violation.taps());

            handle.createUpdate("UPDATE segmentation_findings SET source_addresses = :source_addresses::jsonb, " +
                            "targets = :targets::jsonb, target_count = GREATEST(target_count, :known_targets), " +
                            "session_count = session_count + :sessions, tap_uuids = :tap_uuids::jsonb, " +
                            "audit = :audit, first_seen = LEAST(first_seen, :first_seen), " +
                            "last_seen = GREATEST(last_seen, :last_seen), updated_at = NOW() WHERE id = :id")
                    .bind("id", finding.id())
                    .bind("source_addresses", DnsTunnelJson.toJson(capAddresses(sourceAddresses)))
                    .bind("targets", DnsTunnelJson.toJson(cap(merged.values())))
                    .bind("known_targets", merged.size())
                    .bind("sessions", violation.sessions())
                    .bind("tap_uuids", DnsTunnelJson.toJson(strings(taps)))
                    .bind("audit", audit)
                    .bind("first_seen", violation.firstSeen())
                    .bind("last_seen", violation.lastSeen())
                    .execute();
        }

        if (overLimit > 0) {
            LOG.warn("Policies of tenant [{}/{}] have reached the limit of [{}] findings. [{}] new sources were " +
                    "not recorded.", organizationId, tenantId, MAX_FINDINGS_PER_POLICY, overLimit);
        }

        return opened;
    }

    public Map<Long, Long> countPerPolicy(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> countPerPolicy(handle, organizationId, tenantId));
    }

    private static Map<Long, Long> countPerPolicy(Handle handle, UUID organizationId, UUID tenantId) {
        Map<Long, Long> counts = Maps.newHashMap();
        handle.createQuery("SELECT policy_id, COUNT(*) AS findings FROM segmentation_findings " +
                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id GROUP BY policy_id")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> counts.put(rs.getLong("policy_id"), rs.getLong("findings")))
                .list();

        return counts;
    }

    public void reconcile(Policy policy) {
        MembershipResolver resolver = segmentation.resolver(policy.organizationId(), policy.tenantId());
        PolicyMatcher matcher = new PolicyMatcher(policy);

        nzyme.getDatabase().useTransaction(handle -> {
            // Waits for an evaluation of the tenant to end and keeps the next one out until this is done.
            handle.createQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                    .bind("key", Segmentation.lockKey(policy.organizationId(), policy.tenantId()))
                    .mapToMap()
                    .one();

            long after = 0;
            while (true) {
                List<Finding> batch = handle.createQuery(SELECT + "WHERE f.policy_id = :policy_id AND f.id > :after " +
                                "ORDER BY f.id ASC LIMIT :limit")
                        .bind("policy_id", policy.id())
                        .bind("after", after)
                        .bind("limit", RECONCILE_BATCH_SIZE)
                        .map((rs, ctx) -> map(rs))
                        .list();

                if (batch.isEmpty()) {
                    break;
                }

                after = batch.get(batch.size() - 1).id();
                reconcile(handle, batch, resolver, matcher);
            }
        });
    }

    private void reconcile(Handle handle,
                           List<Finding> findings,
                           MembershipResolver resolver,
                           PolicyMatcher matcher) {
        for (Finding finding : findings) {
            UUID tap = finding.tapUuids().isEmpty() ? new UUID(0, 0) : finding.tapUuids().get(0);

            List<Endpoint> sources = Lists.newArrayList();
            if (finding.sourceAddress() != null) {
                sources.add(resolver.resolve(tap, InetAddresses.forString(finding.sourceAddress()),
                        finding.sourceInternal(), finding.sourceAsn(), finding.sourceCountryCode()));
            } else {
                for (String address : finding.sourceAddresses()) {
                    sources.add(resolver.resolve(tap, InetAddresses.forString(address), finding.sourceInternal(),
                            finding.sourceAsn(), finding.sourceCountryCode()));
                }
            }

            if (sources.isEmpty()) {
                continue;
            }

            List<Finding.Target> remaining = Lists.newArrayList();
            long droppedSessions = 0;
            for (Finding.Target target : finding.targets()) {
                Endpoint destination = destination(resolver, tap, target);

                boolean violates = false;
                for (Endpoint source : sources) {
                    PolicyMatcher.Verdict verdict = matcher.evaluate(source, destination, target.l4Type(),
                            target.port(), target.protocol());
                    if (verdict == PolicyMatcher.Verdict.VIOLATION || verdict == PolicyMatcher.Verdict.INDETERMINATE) {
                        violates = true;
                        break;
                    }
                }

                if (violates) {
                    remaining.add(target);
                } else {
                    droppedSessions += target.sessions();
                }
            }

            if (remaining.size() == finding.targets().size()) {
                continue;
            }

            // Targets beyond the kept ones cannot be judged again. With none of the kept ones left, the finding goes.
            if (remaining.isEmpty()) {
                handle.createUpdate("DELETE FROM segmentation_findings WHERE id = :id")
                        .bind("id", finding.id())
                        .execute();
                continue;
            }

            long dropped = finding.targets().size() - remaining.size();
            handle.createUpdate("UPDATE segmentation_findings SET targets = :targets::jsonb, " +
                            "target_count = GREATEST(target_count - :dropped, :remaining), " +
                            "session_count = GREATEST(session_count - :dropped_sessions, 0), " +
                            "updated_at = NOW() WHERE id = :id")
                    .bind("id", finding.id())
                    .bind("targets", DnsTunnelJson.toJson(remaining))
                    .bind("dropped", dropped)
                    .bind("remaining", remaining.size())
                    .bind("dropped_sessions", droppedSessions)
                    .execute();
        }
    }

    // Nothing but its outcome tells a target that is a broadcast or multicast address apart from a host.
    public static Endpoint destination(MembershipResolver resolver, UUID tap, Finding.Target target) {
        InetAddress address = InetAddresses.forString(target.address());
        if (target.outcome() == Outcome.SENT) {
            return Endpoint.broadcast(address);
        }

        return resolver.resolve(tap, address, target.internal(), target.asn(), target.countryCode());
    }

    private static List<Finding.Target> cap(Collection<Finding.Target> targets) {
        List<Finding.Target> sorted = Lists.newArrayList(targets);
        sorted.sort(Comparator.comparingLong(Finding.Target::lastSeen).reversed()
                .thenComparing(Comparator.comparingLong(Finding.Target::sessions).reversed()));

        return sorted.size() > MAX_TARGETS ? Lists.newArrayList(sorted.subList(0, MAX_TARGETS)) : sorted;
    }

    private static List<String> capAddresses(Collection<String> addresses) {
        List<String> capped = Lists.newArrayList();
        for (String address : addresses) {
            if (capped.size() >= MAX_SOURCE_ADDRESSES) {
                break;
            }
            capped.add(address);
        }

        return capped;
    }

    private static List<String> strings(Collection<UUID> uuids) {
        List<String> strings = Lists.newArrayList();
        for (UUID uuid : uuids) {
            strings.add(uuid.toString());
        }

        return strings;
    }

    private static Finding map(ResultSet rs) throws SQLException {
        long asnValue = rs.getLong("source_asn");
        Long asn = rs.wasNull() ? null : asnValue;

        List<UUID> taps = DnsTunnelJson.parseUuids(rs.getString("tap_uuids"));

        return new Finding(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getLong("policy_id"),
                UUID.fromString(rs.getString("policy_uuid")),
                rs.getString("policy_name"),
                FindingClass.valueOf(rs.getString("finding_class")),
                rs.getString("source_key"),
                rs.getString("source_address_host"),
                rs.getBoolean("source_internal"),
                asn,
                rs.getString("source_country_code"),
                DnsTunnelJson.parseStrings(rs.getString("source_addresses")),
                SegmentationJson.parseTargets(rs.getString("targets")),
                rs.getLong("target_count"),
                rs.getLong("session_count"),
                taps,
                rs.getBoolean("audit"),
                new DateTime(rs.getTimestamp("first_seen")),
                new DateTime(rs.getTimestamp("last_seen")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}
