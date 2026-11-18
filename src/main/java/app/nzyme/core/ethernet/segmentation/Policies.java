package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.dns.tunnels.DnsTunnelJson;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.segmentation.filters.PolicyFilters;
import app.nzyme.core.util.TenantCacheKey;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.Filters;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.Update;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Policies {

    public static final int NAME_MAX_LENGTH = 64;
    public static final int MAX_ALLOWED = 250;
    private static final int MAX_REMEMBERED_ALLOWED = 4 * MAX_ALLOWED;
    public static final int MAX_SELECTORS = 50;
    public static final int MAX_SERVICES = 50;

    public enum OrderColumn {

        NAME("lower(p.name)"),
        STATUS("p.status"),
        CROSSED_FINDINGS(PolicyFilters.CROSSED_FINDINGS),
        ATTEMPTED_FINDINGS(PolicyFilters.ATTEMPTED_FINDINGS),
        LAST_FINDING("f.last_finding"),
        CREATED_AT("p.created_at"),
        UPDATED_AT("p.updated_at");

        private final String columnName;

        OrderColumn(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    public record Listed(Policy policy, Findings.PolicySummary summary) { }

    private static final String LISTED = "FROM segmentation_policies AS p LEFT JOIN (" +
            "SELECT policy_id, COUNT(*) FILTER (WHERE finding_class = 'CROSSED') AS crossed_findings, " +
            "COUNT(*) FILTER (WHERE finding_class = 'ATTEMPTED') AS attempted_findings, " +
            "MAX(last_seen) FILTER (WHERE finding_class = 'CROSSED') AS last_crossed, " +
            "MAX(last_seen) FILTER (WHERE finding_class = 'ATTEMPTED') AS last_attempted, " +
            "MAX(last_seen) AS last_finding FROM segmentation_findings " +
            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id AND last_seen >= :since " +
            "GROUP BY policy_id) AS f ON f.policy_id = p.id " +
            "WHERE p.organization_id = :organization_id AND p.tenant_id = :tenant_id ";

    private static final String NO_BACKFILL = "backfill_requested_at = NULL, backfill_cursor = NULL, " +
            "backfill_from = NULL, backfill_previous_scope = NULL, backfill_previous_allowed = NULL, " +
            "backfill_previous_until = NULL";

    // The engine sets the cursor, and only if the policy has no findings: nothing says what they were recorded for.
    private static final String BACKFILL_WITHOUT_CURSOR = "backfill_requested_at = clock_timestamp(), " +
            "backfill_cursor = NULL, backfill_from = NULL, backfill_previous_scope = NULL, " +
            "backfill_previous_allowed = NULL, backfill_previous_until = NULL";

    /*
     * A policy that is paused keeps what it was evaluated against and up to where, so that the sessions of the pause
     * can be judged when it is resumed. One that is paused while stored sessions are still judged against it keeps
     * nothing: what its findings were recorded for can then not be said.
     */
    private static final String PAUSING = "(:pausing AND status <> :paused)";
    private static final String RESUMING = "(NOT :pausing AND status = :paused)";
    private static final String REMEMBERED = "(backfill_requested_at IS NULL AND CAST(:watermark AS timestamptz) IS NOT NULL)";

    private final NzymeNode nzyme;
    private final Segmentation segmentation;

    public Policies(NzymeNode nzyme, Segmentation segmentation) {
        this.nzyme = nzyme;
        this.segmentation = segmentation;
    }

    public List<Policy> findAll(UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle -> findAll(handle, organizationId, tenantId));
    }

    public List<Policy> findAll(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM segmentation_policies WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id ORDER BY lower(name) ASC")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .map((rs, ctx) -> map(rs))
                .list();
    }

    public long count(Filters filters, UUID organizationId, UUID tenantId, DateTime since) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new PolicyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) " + LISTED + filterFragment.whereSql())
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("since", since)
                        .bindMap(filterFragment.bindings())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Listed> findAll(Filters filters,
                                OrderColumn orderColumn,
                                OrderDirection orderDirection,
                                int limit,
                                int offset,
                                UUID organizationId,
                                UUID tenantId,
                                DateTime since) {
        FilterSqlFragment filterFragment = FilterSql.generate(filters, new PolicyFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT p.*, " + PolicyFilters.CROSSED_FINDINGS + " AS crossed_findings, " +
                                PolicyFilters.ATTEMPTED_FINDINGS + " AS attempted_findings, f.last_crossed, " +
                                "f.last_attempted " + LISTED + filterFragment.whereSql() +
                                "ORDER BY <order_column> <order_direction> NULLS LAST, lower(p.name) ASC, p.id ASC " +
                                "LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("since", since)
                        .bindMap(filterFragment.bindings())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .define("order_column", orderColumn.getColumnName())
                        .define("order_direction", orderDirection)
                        .map((rs, ctx) -> new Listed(map(rs), new Findings.PolicySummary(
                                rs.getLong("crossed_findings"),
                                rs.getLong("attempted_findings"),
                                rs.getTimestamp("last_crossed") == null ? null : new DateTime(rs.getTimestamp("last_crossed")),
                                rs.getTimestamp("last_attempted") == null ? null : new DateTime(rs.getTimestamp("last_attempted"))
                        )))
                        .list()
        );
    }

    // Filters are the ones of the list, findings of the time range included, so that a bulk action reaches exactly the
    // rows the list shows.
    private record Targets(String sql, Map<String, Object> bindings, @Nullable List<UUID> uuids) {

        <S extends SqlStatement<S>> S bind(S statement) {
            statement.bindMap(bindings);
            if (uuids != null) {
                statement.bindList("uuids", uuids);
            }

            return statement;
        }

    }

    private static Targets targets(BulkSelection selection, UUID organizationId, UUID tenantId, DateTime since) {
        Map<String, Object> bindings = Maps.newHashMap();
        bindings.put("organization_id", organizationId);
        bindings.put("tenant_id", tenantId);

        if (selection.isChecked()) {
            return new Targets("SELECT p.id FROM segmentation_policies AS p WHERE p.organization_id = :organization_id " +
                    "AND p.tenant_id = :tenant_id AND p.uuid IN (<uuids>)", bindings, selection.uuids());
        }

        FilterSqlFragment filterFragment = FilterSql.generate(selection.filters(), new PolicyFilters());
        bindings.put("since", since);
        bindings.putAll(filterFragment.bindings());

        return new Targets("SELECT p.id " + LISTED + filterFragment.whereSql(), bindings, null);
    }

    public int setStatus(BulkSelection selection, PolicyStatus status, UUID organizationId, UUID tenantId, DateTime since) {
        if (selection.isEmpty()) {
            return 0;
        }

        Targets targets = targets(selection, organizationId, tenantId, since);
        int updated = nzyme.getDatabase().inTransaction(handle -> {
            DateTime watermark = lockedWatermark(handle, organizationId, tenantId);

            return targets.bind(handle.createUpdate("UPDATE segmentation_policies SET " +
                            "backfill_requested_at = CASE WHEN :pausing THEN NULL WHEN " + RESUMING +
                            " THEN clock_timestamp() ELSE backfill_requested_at END, " +
                            "backfill_cursor = CASE WHEN :pausing THEN NULL WHEN " + RESUMING +
                            " THEN CASE WHEN backfill_previous_until IS NOT NULL " +
                            "THEN CAST(:watermark AS timestamptz) END ELSE backfill_cursor END, " +
                            "backfill_from = CASE WHEN :pausing OR " + RESUMING + " THEN NULL ELSE backfill_from END, " +
                            "backfill_previous_scope = CASE WHEN " + PAUSING + " THEN CASE WHEN " + REMEMBERED +
                            " THEN scope END ELSE backfill_previous_scope END, " +
                            "backfill_previous_allowed = CASE WHEN " + PAUSING + " THEN CASE WHEN " + REMEMBERED +
                            " THEN allowed END ELSE backfill_previous_allowed END, " +
                            "backfill_previous_until = CASE WHEN " + PAUSING + " THEN CASE WHEN " + REMEMBERED +
                            " THEN CAST(:watermark AS timestamptz) END ELSE backfill_previous_until END, " +
                            "status = :status, updated_at = clock_timestamp() WHERE id IN (" + targets.sql() + ")"))
                    .bind("status", status.name())
                    .bind("pausing", status == PolicyStatus.PAUSED)
                    .bind("paused", PolicyStatus.PAUSED.name())
                    .bindByType("watermark", watermark, DateTime.class)
                    .execute();
        });

        segmentation.invalidateResolver(organizationId, tenantId);
        segmentation.judgeStoredSessions(organizationId, tenantId);

        return updated;
    }

    public int setAlert(BulkSelection selection,
                        FindingClass findingClass,
                        boolean enabled,
                        UUID organizationId,
                        UUID tenantId,
                        DateTime since) {
        if (selection.isEmpty()) {
            return 0;
        }

        String column = findingClass == FindingClass.CROSSED ? "alert_on_connected" : "alert_on_attempted";

        Targets targets = targets(selection, organizationId, tenantId, since);
        int updated = nzyme.getDatabase().withHandle(handle ->
                targets.bind(handle.createUpdate("UPDATE segmentation_policies SET " + column + " = :enabled, " +
                                "updated_at = clock_timestamp() WHERE id IN (" + targets.sql() + ")"))
                        .bind("enabled", enabled)
                        .execute()
        );

        segmentation.invalidateResolver(organizationId, tenantId);
        return updated;
    }

    public int delete(BulkSelection selection, UUID organizationId, UUID tenantId, DateTime since) {
        if (selection.isEmpty()) {
            return 0;
        }

        Targets targets = targets(selection, organizationId, tenantId, since);
        int deleted = nzyme.getDatabase().withHandle(handle ->
                targets.bind(handle.createUpdate("DELETE FROM segmentation_policies WHERE id IN (" +
                                targets.sql() + ")"))
                        .execute()
        );

        segmentation.invalidateResolver(organizationId, tenantId);
        return deleted;
    }

    public static Map<UUID, List<String>> segmentReferences(Collection<Policy> policies) {
        Map<UUID, List<String>> references = Maps.newHashMap();
        for (Policy policy : policies) {
            for (PolicyRule rule : rules(policy)) {
                for (PolicyRule.Selector selector : selectors(rule)) {
                    if (selector.type() != PolicyRule.SelectorType.SEGMENT || selector.value() == null) {
                        continue;
                    }

                    UUID segment;
                    try {
                        segment = UUID.fromString(selector.value());
                    } catch (IllegalArgumentException e) {
                        continue;
                    }

                    List<String> names = references.computeIfAbsent(segment, k -> Lists.newArrayList());
                    if (!names.contains(policy.name())) {
                        names.add(policy.name());
                    }
                }
            }
        }

        return references;
    }

    public Optional<Policy> find(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM segmentation_policies WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .map((rs, ctx) -> map(rs))
                        .findOne()
        );
    }

    public Policy create(UUID organizationId,
                         UUID tenantId,
                         String name,
                         @Nullable String description,
                         PolicyStatus status,
                         boolean alertOnConnected,
                         boolean alertOnAttempted,
                         PolicyRule scope,
                         List<PolicyRule> allowed) {
        validate(organizationId, tenantId, name, scope, allowed);

        UUID uuid = UUID.randomUUID();
        nzyme.getDatabase().useTransaction(handle -> {
            // A policy that is not evaluated is not judged against stored sessions either. Neither is one of a
            // tenant the engine has not started on: its first run evaluates with the policy in place.
            DateTime watermark = status == PolicyStatus.PAUSED ? null
                    : lockedWatermark(handle, organizationId, tenantId);

            handle.createUpdate("INSERT INTO segmentation_policies(uuid, organization_id, tenant_id, name, " +
                            "description, status, alert_on_connected, alert_on_attempted, scope, allowed, " +
                            "created_at, updated_at, backfill_requested_at, backfill_cursor) VALUES(:uuid, " +
                            ":organization_id, :tenant_id, :name, :description, :status, :alert_on_connected, " +
                            ":alert_on_attempted, :scope::jsonb, :allowed::jsonb, clock_timestamp(), " +
                            "clock_timestamp(), CASE WHEN :backfill THEN clock_timestamp() END, :watermark)")
                    .bind("uuid", uuid)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .bind("name", name.trim())
                    .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                    .bind("status", status.name())
                    .bind("alert_on_connected", alertOnConnected)
                    .bind("alert_on_attempted", alertOnAttempted)
                    .bind("scope", DnsTunnelJson.toJson(scope))
                    .bind("allowed", DnsTunnelJson.toJson(allowed))
                    .bind("backfill", watermark != null)
                    .bindByType("watermark", watermark, DateTime.class)
                    .execute();
        });

        segmentation.invalidateResolver(organizationId, tenantId);
        segmentation.judgeStoredSessions(organizationId, tenantId);

        return find(uuid, organizationId, tenantId).orElseThrow();
    }

    public Policy update(Policy policy,
                         String name,
                         @Nullable String description,
                         PolicyStatus status,
                         boolean alertOnConnected,
                         boolean alertOnAttempted,
                         PolicyRule scope,
                         List<PolicyRule> allowed) {
        validate(policy.organizationId(), policy.tenantId(), name, scope, allowed);

        nzyme.getDatabase().useTransaction(handle -> {
            DateTime watermark = lockedWatermark(handle, policy.organizationId(), policy.tenantId());

            Optional<Policy> stored = handle.createQuery("SELECT * FROM segmentation_policies WHERE id = :id FOR UPDATE")
                    .bind("id", policy.id())
                    .map((rs, ctx) -> map(rs))
                    .findOne();
            if (stored.isEmpty()) {
                return;
            }

            Policy current = stored.get();

            String backfill = "";
            PolicyRule previousScope = null;
            List<PolicyRule> previousAllowed = null;
            boolean cursor = false;
            if (status == PolicyStatus.PAUSED && current.status() != PolicyStatus.PAUSED) {
                if (current.backfillRequestedAt() == null && watermark != null) {
                    previousScope = current.scope();
                    previousAllowed = remembered(current.allowed(), allowed);
                    cursor = true;
                    backfill = ", backfill_previous_scope = :previous_scope::jsonb, " +
                            "backfill_previous_allowed = :previous_allowed::jsonb, backfill_previous_until = :watermark";
                } else {
                    backfill = ", " + NO_BACKFILL;
                }
            } else if (status == PolicyStatus.PAUSED) {
                if (current.backfillPreviousScope() != null) {
                    previousAllowed = remembered(current.backfillPreviousAllowed(), allowed);
                    if (previousAllowed.size() > MAX_REMEMBERED_ALLOWED) {
                        previousAllowed = null;
                        backfill = ", " + NO_BACKFILL;
                    } else {
                        previousScope = current.backfillPreviousScope();
                        backfill = ", backfill_previous_scope = :previous_scope::jsonb, " +
                                "backfill_previous_allowed = :previous_allowed::jsonb";
                    }
                }
            } else if (current.status() == PolicyStatus.PAUSED) {
                if (current.backfillPreviousUntil() != null && watermark != null) {
                    cursor = true;
                    backfill = ", backfill_requested_at = clock_timestamp(), backfill_cursor = :watermark, " +
                            "backfill_from = NULL";
                } else {
                    backfill = ", " + BACKFILL_WITHOUT_CURSOR;
                }
            } else if (forbidsMore(scope, allowed, current.scope(), current.allowed())) {
                // The sessions that are not judged yet would have to be judged against two previous definitions.
                if (current.backfillRequestedAt() != null) {
                    throw new IllegalArgumentException("Stored sessions are still being judged against this policy. " +
                            "Until that has ended, the scope cannot be changed and allowed crossings cannot be removed.");
                }

                if (watermark != null) {
                    previousScope = current.scope();
                    previousAllowed = current.allowed();
                    cursor = true;
                    backfill = ", backfill_requested_at = clock_timestamp(), backfill_cursor = :watermark, " +
                            "backfill_from = NULL, backfill_previous_scope = :previous_scope::jsonb, " +
                            "backfill_previous_allowed = :previous_allowed::jsonb, backfill_previous_until = NULL";
                }
            }

            // The time of the write and not of the start of the transaction, which waited for the engine: the
            // engine tells by the latest time whether it has loaded all changes.
            Update update = handle.createUpdate("UPDATE segmentation_policies SET name = :name, " +
                            "description = :description, status = :status, alert_on_connected = :alert_on_connected, " +
                            "alert_on_attempted = :alert_on_attempted, scope = :scope::jsonb, " +
                            "allowed = :allowed::jsonb, updated_at = clock_timestamp()" + backfill + " WHERE id = :id")
                    .bind("id", policy.id())
                    .bind("name", name.trim())
                    .bind("description", Strings.emptyToNull(Strings.nullToEmpty(description).trim()))
                    .bind("status", status.name())
                    .bind("alert_on_connected", alertOnConnected)
                    .bind("alert_on_attempted", alertOnAttempted)
                    .bind("scope", DnsTunnelJson.toJson(scope))
                    .bind("allowed", DnsTunnelJson.toJson(allowed));

            if (cursor) {
                update.bind("watermark", watermark);
            }

            if (previousScope != null) {
                update.bind("previous_scope", DnsTunnelJson.toJson(previousScope))
                        .bind("previous_allowed", DnsTunnelJson.toJson(previousAllowed));
            }

            update.execute();
        });

        segmentation.invalidateResolver(policy.organizationId(), policy.tenantId());

        Policy updated = find(policy.uuid(), policy.organizationId(), policy.tenantId()).orElseThrow();

        if (!policy.scope().equals(scope) || !policy.allowed().equals(allowed)) {
            segmentation.findings().reconcile(updated);
        }

        segmentation.judgeStoredSessions(policy.organizationId(), policy.tenantId());

        return updated;
    }

    // With the same scope and nothing taken off the allowed crossings, no session can turn into a finding.
    static boolean forbidsMore(PolicyRule scope,
                               List<PolicyRule> allowed,
                               PolicyRule previousScope,
                               List<PolicyRule> previousAllowed) {
        return !previousScope.equals(scope) || !allowed.containsAll(previousAllowed);
    }

    // Findings of a paused policy go when it allows their crossing, and nothing brings them back if it forbids the
    // crossing again before it is resumed. What it allowed in between is remembered as allowed before, so that the
    // stored sessions of such a crossing are judged again.
    private static List<PolicyRule> remembered(List<PolicyRule> before, List<PolicyRule> allowed) {
        Set<PolicyRule> all = Sets.newLinkedHashSet(before);
        all.addAll(allowed);

        return Lists.newArrayList(all);
    }

    // No evaluation of the tenant runs while the lock is held. Everything before the watermark was evaluated against
    // the policies as they were, everything after it will be evaluated against what this transaction writes.
    private DateTime lockedWatermark(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .bind("key", Segmentation.lockKey(organizationId, tenantId))
                .mapToMap()
                .one();

        return segmentation.state(handle, organizationId, tenantId, false).watermark();
    }

    // The policy that was changed last comes first: it is the one that is being looked at. Locked, a policy cannot
    // be changed until its backfill is written back. One that is being changed right now is left for later.
    public List<Policy> findAllWithPendingBackfill(Handle handle, UUID organizationId, UUID tenantId) {
        return handle.createQuery("SELECT * FROM segmentation_policies WHERE organization_id = :organization_id " +
                        "AND tenant_id = :tenant_id AND backfill_requested_at IS NOT NULL AND status <> :paused " +
                        "ORDER BY backfill_requested_at DESC, id DESC FOR UPDATE SKIP LOCKED")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .bind("paused", PolicyStatus.PAUSED.name())
                .map((rs, ctx) -> map(rs))
                .list();
    }

    public List<TenantCacheKey> findTenantsWithPendingBackfill() {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT DISTINCT organization_id, tenant_id FROM segmentation_policies " +
                                "WHERE backfill_requested_at IS NOT NULL AND status <> :paused " +
                                "ORDER BY organization_id, tenant_id")
                        .bind("paused", PolicyStatus.PAUSED.name())
                        .map((rs, ctx) -> TenantCacheKey.create(UUID.fromString(rs.getString("organization_id")),
                                UUID.fromString(rs.getString("tenant_id"))))
                        .list()
        );
    }

    // The time of the last change stays as it is: the engine takes a new one for a change of the definitions.
    public void setBackfill(Handle handle, Collection<Policy> policies, DateTime cursor, DateTime from) {
        handle.createUpdate("UPDATE segmentation_policies SET backfill_cursor = :cursor, backfill_from = :from " +
                        "WHERE id IN (<ids>)")
                .bind("cursor", cursor)
                .bind("from", from)
                .bindList("ids", ids(policies))
                .execute();
    }

    public void completeBackfill(Handle handle, Collection<Policy> policies) {
        handle.createUpdate("UPDATE segmentation_policies SET " + NO_BACKFILL + " WHERE id IN (<ids>)")
                .bindList("ids", ids(policies))
                .execute();
    }

    public void cancelBackfills(Handle handle, UUID organizationId, UUID tenantId) {
        handle.createUpdate("UPDATE segmentation_policies SET " + NO_BACKFILL + " WHERE organization_id = " +
                        ":organization_id AND tenant_id = :tenant_id AND backfill_requested_at IS NOT NULL")
                .bind("organization_id", organizationId)
                .bind("tenant_id", tenantId)
                .execute();
    }

    private static List<Long> ids(Collection<Policy> policies) {
        List<Long> ids = Lists.newArrayList();
        for (Policy policy : policies) {
            ids.add(policy.id());
        }

        return ids;
    }

    public Policy allow(Policy policy, PolicyRule rule) {
        List<PolicyRule> allowed = Lists.newArrayList(policy.allowed());
        if (!allowed.contains(rule)) {
            allowed.add(rule);
        }

        return update(policy, policy.name(), policy.description(), policy.status(), policy.alertOnConnected(),
                policy.alertOnAttempted(), policy.scope(), allowed);
    }

    public void delete(Policy policy) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM segmentation_policies WHERE id = :id")
                        .bind("id", policy.id())
                        .execute()
        );

        segmentation.invalidateResolver(policy.organizationId(), policy.tenantId());
    }

    public List<Policy> findReferencing(UUID organizationId, UUID tenantId, PolicyRule.SelectorType type, UUID uuid) {
        List<Policy> result = Lists.newArrayList();
        for (Policy policy : findAll(organizationId, tenantId)) {
            if (references(policy, type, uuid.toString())) {
                result.add(policy);
            }
        }

        return result;
    }

    public static Set<UUID> referencedAssets(Collection<Policy> policies) {
        Set<UUID> assets = Sets.newHashSet();
        for (Policy policy : policies) {
            for (PolicyRule rule : rules(policy)) {
                for (PolicyRule.Selector selector : selectors(rule)) {
                    if (selector.type() == PolicyRule.SelectorType.ASSET && selector.value() != null) {
                        assets.add(UUID.fromString(selector.value()));
                    }
                }
            }
        }

        return assets;
    }

    public static Set<UUID> referencedAssets(PolicyRule scope, List<PolicyRule> allowed) {
        Set<UUID> assets = Sets.newHashSet();
        for (PolicyRule rule : rules(scope, allowed)) {
            for (PolicyRule.Selector selector : selectors(rule)) {
                if (selector.type() == PolicyRule.SelectorType.ASSET && selector.value() != null) {
                    assets.add(UUID.fromString(selector.value()));
                }
            }
        }

        return assets;
    }

    public static boolean references(Policy policy, PolicyRule.SelectorType type, String value) {
        for (PolicyRule rule : rules(policy)) {
            for (PolicyRule.Selector selector : selectors(rule)) {
                if (selector.type() == type && value.equalsIgnoreCase(selector.value())) {
                    return true;
                }
            }
        }

        return false;
    }

    // With the previous definition: stored sessions are judged against it for as long as it is kept, so what it
    // names has to stay.
    private static List<PolicyRule> rules(Policy policy) {
        List<PolicyRule> rules = rules(policy.scope(), policy.allowed());
        if (policy.backfillPreviousScope() != null) {
            rules.addAll(rules(policy.backfillPreviousScope(), policy.backfillPreviousAllowed()));
        }

        return rules;
    }

    private static List<PolicyRule> rules(PolicyRule scope, List<PolicyRule> allowed) {
        List<PolicyRule> rules = Lists.newArrayList(scope);
        rules.addAll(allowed);
        return rules;
    }

    private static List<PolicyRule.Selector> selectors(PolicyRule rule) {
        List<PolicyRule.Selector> selectors = Lists.newArrayList(rule.sources());
        selectors.addAll(rule.destinations());
        return selectors;
    }

    public void validate(UUID organizationId,
                         UUID tenantId,
                         @Nullable String name,
                         @Nullable PolicyRule scope,
                         @Nullable List<PolicyRule> allowed) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }

        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("The name must not be longer than " + NAME_MAX_LENGTH + " characters.");
        }

        validateDefinition(organizationId, tenantId, scope, allowed);
    }

    public void validateDefinition(UUID organizationId,
                                   UUID tenantId,
                                   @Nullable PolicyRule scope,
                                   @Nullable List<PolicyRule> allowed) {
        if (scope == null) {
            throw new IllegalArgumentException("A scope is required.");
        }

        if (allowed == null) {
            throw new IllegalArgumentException("The list of allowed crossings is missing.");
        }

        if (allowed.size() > MAX_ALLOWED) {
            throw new IllegalArgumentException("No more than " + MAX_ALLOWED + " allowed crossings per policy.");
        }

        if (isUnbounded(scope.sources()) && isUnbounded(scope.destinations())) {
            throw new IllegalArgumentException("The scope must name a source or a destination.");
        }

        Set<UUID> segments = Sets.newHashSet();
        for (Segment segment : segmentation.segments().findAll(organizationId, tenantId)) {
            segments.add(segment.uuid());
        }

        Set<UUID> tags = Sets.newHashSet();
        nzyme.getAssetsManager().tags().findAll(organizationId, tenantId).forEach(tag -> tags.add(tag.uuid()));

        validateRule("scope", scope, organizationId, tenantId, segments, tags);
        int i = 1;
        for (PolicyRule rule : allowed) {
            if (rule == null) {
                throw new IllegalArgumentException("Allowed crossing " + i + " is empty.");
            }

            validateRule("allowed crossing " + i, rule, organizationId, tenantId, segments, tags);

            if (allowsEverything(rule) && !saysEverything(rule)) {
                throw new IllegalArgumentException("Allowed crossing " + i + " names no source, destination or " +
                        "service.");
            }

            i++;
        }
    }

    static boolean allowsEverything(PolicyRule rule) {
        return isUnbounded(rule.sources()) && isUnbounded(rule.destinations()) && rule.services().isEmpty();
    }

    // The one allowed crossing that may allow everything in scope is the one that says so, with anything as its only
    // source and destination. One that allows everything by naming nothing is taken for unfinished and refused.
    static boolean saysEverything(PolicyRule rule) {
        return isAnything(rule.sources()) && isAnything(rule.destinations()) && rule.services().isEmpty();
    }

    private static boolean isAnything(List<PolicyRule.Selector> selectors) {
        return selectors.size() == 1 && selectors.get(0) != null
                && selectors.get(0).type() == PolicyRule.SelectorType.ANY;
    }

    private static boolean isUnbounded(List<PolicyRule.Selector> selectors) {
        if (selectors.isEmpty()) {
            return true;
        }

        for (PolicyRule.Selector selector : selectors) {
            if (selector != null && selector.type() == PolicyRule.SelectorType.ANY) {
                return true;
            }
        }

        return false;
    }

    private void validateRule(String where,
                              PolicyRule rule,
                              UUID organizationId,
                              UUID tenantId,
                              Set<UUID> segments,
                              Set<UUID> tags) {
        if (rule.sources().size() > MAX_SELECTORS || rule.destinations().size() > MAX_SELECTORS) {
            throw new IllegalArgumentException("Too many sources or destinations in the " + where + ".");
        }

        if (rule.services().size() > MAX_SERVICES) {
            throw new IllegalArgumentException("Too many services in the " + where + ".");
        }

        for (PolicyRule.Selector selector : rule.sources()) {
            if (selector != null && selector.type() == PolicyRule.SelectorType.BROADCAST) {
                throw new IllegalArgumentException("Broadcast and multicast addresses can only be a destination, " +
                        "not a source in the " + where + ".");
            }
        }

        for (PolicyRule.Selector selector : selectors(rule)) {
            if (selector == null || selector.type() == null) {
                throw new IllegalArgumentException("A source or destination in the " + where + " has no type.");
            }

            if (!selector.type().hasValue()) {
                continue;
            }

            String value = selector.value();
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("A source or destination in the " + where + " has no value.");
            }

            switch (selector.type()) {
                case SEGMENT:
                    if (!segments.contains(uuid(value, where))) {
                        throw new IllegalArgumentException("The " + where + " names a segment that does not exist.");
                    }
                    break;
                case TAG:
                    if (!tags.contains(uuid(value, where))) {
                        throw new IllegalArgumentException("The " + where + " names a tag that does not exist.");
                    }
                    break;
                case ASSET:
                    if (nzyme.getAssetsManager().findAsset(uuid(value, where), organizationId, tenantId).isEmpty()) {
                        throw new IllegalArgumentException("The " + where + " names an asset that does not exist.");
                    }
                    break;
                case ADDRESS:
                    try {
                        CIDR.parse(value);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("\"" + value + "\" in the " + where + " is not an address " +
                                "or a network in CIDR notation.");
                    }
                    break;
                case ASN:
                    try {
                        long asn = Long.parseLong(value);
                        if (asn <= 0 || asn > 4294967295L) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("\"" + value + "\" in the " + where + " is not an " +
                                "autonomous system number.");
                    }
                    break;
                case COUNTRY:
                    if (!value.matches("\\p{L}{2}")) {
                        throw new IllegalArgumentException("\"" + value + "\" in the " + where + " is not a " +
                                "two-letter country code.");
                    }
                    break;
                default:
                    break;
            }
        }

        for (PolicyRule.Service service : rule.services()) {
            if (service == null) {
                throw new IllegalArgumentException("A service in the " + where + " is empty.");
            }

            if (service.l4Type() != null && !service.l4Type().equals("TCP") && !service.l4Type().equals("UDP")) {
                throw new IllegalArgumentException("A service in the " + where + " must be TCP or UDP.");
            }

            if (service.portFrom() == null && service.portTo() != null) {
                throw new IllegalArgumentException("A port range in the " + where + " has no start.");
            }

            if (service.portFrom() != null) {
                int to = service.portTo() == null ? service.portFrom() : service.portTo();
                if (service.portFrom() < 0 || to > 65535 || service.portFrom() > to) {
                    throw new IllegalArgumentException("A port or port range in the " + where + " is invalid.");
                }
            }

            if (service.protocol() != null && service.protocol().isBlank()) {
                throw new IllegalArgumentException("A protocol in the " + where + " is invalid.");
            }

            if (service.protocol() != null && !PolicyRule.isKnownProtocol(service.protocol())) {
                String name = service.protocol().length() > 32 ? service.protocol().substring(0, 32) : service.protocol();
                throw new IllegalArgumentException("\"" + name + "\" in the " + where + " is not a protocol that taps " +
                        "detect. Known are " + String.join(", ", PolicyRule.PROTOCOLS) + ".");
            }

            if (service.l4Type() == null && service.portFrom() == null && service.protocol() == null) {
                throw new IllegalArgumentException("A service in the " + where + " names no protocol and no port.");
            }
        }
    }

    private static UUID uuid(String value, String where) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The " + where + " contains an invalid reference.");
        }
    }

    private static Policy map(ResultSet rs) throws SQLException {
        return new Policy(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("organization_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("name"),
                rs.getString("description"),
                PolicyStatus.valueOf(rs.getString("status")),
                rs.getBoolean("alert_on_connected"),
                rs.getBoolean("alert_on_attempted"),
                SegmentationJson.parseRule(rs.getString("scope")),
                SegmentationJson.parseRules(rs.getString("allowed")),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at")),
                Segmentation.dateTime(rs, "backfill_requested_at"),
                Segmentation.dateTime(rs, "backfill_cursor"),
                Segmentation.dateTime(rs, "backfill_from"),
                rs.getString("backfill_previous_scope") == null ? null
                        : SegmentationJson.parseRule(rs.getString("backfill_previous_scope")),
                SegmentationJson.parseRules(rs.getString("backfill_previous_allowed")),
                Segmentation.dateTime(rs, "backfill_previous_until")
        );
    }

}
