package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingItems;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.briefing.BriefingSeverity;
import app.nzyme.core.events.types.SystemEventCategory;
import app.nzyme.core.events.types.SystemEventType;
import app.nzyme.core.monitoring.health.IndicatorStatusLevel;
import app.nzyme.core.monitoring.health.db.IndicatorStatus;
import app.nzyme.core.rest.responses.briefing.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class SystemCollector {

    private static final Logger LOG = LogManager.getLogger(SystemCollector.class);

    private final BriefingContext ctx;

    private static final int MAX_BREAKDOWN_ENTRIES = 8;
    private static final int MAX_SAMPLES = 6;

    private record NodeState(String name, boolean online, DateTime lastSeen, String version) { }

    private record EventCategoryCounts(long current, long previous, List<BriefingBreakdownEntryResponse> breakdown) { }

    private record AccessData(long logins,
                              List<BriefingSampleResponse> loginSamples,
                              long sessions,
                              long apiKeysUsed,
                              List<BriefingSampleResponse> apiKeySamples,
                              EventCategoryCounts authenticationEvents) { }

    private Map<String, String> healthIndicators;
    private long detectionSubscriptions;
    private List<NodeState> nodes;
    private AccessData organizationAccess;
    private AccessData superAdministratorAccess;
    private EventCategoryCounts healthEvents;

    public SystemCollector(BriefingContext ctx) {
        this.ctx = ctx;
    }

    public SystemCollector load() {
        if (ctx.isSuperAdministrator()) {
            healthIndicators = Maps.newTreeMap();
            for (IndicatorStatus status : ctx.nzyme().getHealthMonitor().getIndicatorStatus()) {
                // UNAVAILABLE means not applicable, for example Connect while Connect is off.
                if (!status.active() || status.resultLevel() == IndicatorStatusLevel.UNAVAILABLE) {
                    continue;
                }
                healthIndicators.put(status.indicatorId(), status.resultLevel().name());
            }
        }

        if (ctx.isOrganizationAdministratorOrAbove()) {
            organizationAccess = loadAccess("u.organization_id = :organization_id",
                    q -> q.bind("organization_id", ctx.organizationId()),
                    eventCategoryCounts(ctx.organizationId(), SystemEventCategory.AUTHENTICATION));
        }

        if (ctx.isSuperAdministrator()) {
            loadAdministratorData();
        }

        detectionSubscriptions = ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM event_subscriptions " +
                                "WHERE organization_id = :organization_id AND event_type = 'DETECTION'")
                        .bind("organization_id", ctx.organizationId())
                        .mapTo(Long.class)
                        .one()
        );

        return this;
    }

    public @Nullable Map<String, String> healthIndicators() {
        return healthIndicators;
    }

    private void loadAdministratorData() {
        nodes = ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT name, last_seen, version FROM nodes WHERE deleted = false ORDER BY name")
                        .map((rs, c) -> {
                            DateTime lastSeen = rs.getTimestamp("last_seen") == null
                                    ? null : new DateTime(rs.getTimestamp("last_seen"));
                            return new NodeState(rs.getString("name"),
                                    lastSeen != null && lastSeen.isAfter(DateTime.now().minusMinutes(2)),
                                    lastSeen, rs.getString("version"));
                        })
                        .list()
        );

        superAdministratorAccess = loadAccess("u.is_superadmin = true", q -> {},
                eventCategoryCounts(null, SystemEventCategory.AUTHENTICATION));
        healthEvents = eventCategoryCounts(null, SystemEventCategory.HEALTH_INDICATOR);
    }

    // userScope is a condition on auth_users aliased "u". Logins are not events and sessions vanish on logout,
    // so last_login_at and the API key last_activity are the only traces: distinct users and keys, no deltas.
    private AccessData loadAccess(String userScope, Consumer<Query> binder, EventCategoryCounts authenticationEvents) {
        List<BriefingSampleResponse> users = ctx.nzyme().getDatabase().withHandle(handle -> {
            Query q = handle.createQuery("SELECT u.email, u.name, u.last_login_at FROM auth_users AS u " +
                            "WHERE " + userScope + " AND u.last_login_at >= :from AND u.last_login_at <= :to " +
                            "ORDER BY u.last_login_at DESC")
                    .bind("from", ctx.window().from())
                    .bind("to", ctx.window().to());
            binder.accept(q);
            return q.map((rs, c) -> BriefingItems.sample(rs.getString("name"), rs.getString("email"),
                            new DateTime(rs.getTimestamp("last_login_at")), null, null))
                    .list();
        });

        long sessions = ctx.nzyme().getDatabase().withHandle(handle -> {
            Query q = handle.createQuery("SELECT COUNT(*) FROM auth_sessions AS s " +
                    "JOIN auth_users AS u ON u.uuid = s.user_id WHERE " + userScope);
            binder.accept(q);
            return q.mapTo(Long.class).one();
        });

        List<BriefingSampleResponse> keys = ctx.nzyme().getDatabase().withHandle(handle -> {
            Query q = handle.createQuery("SELECT k.name AS key_name, k.last_activity, u.email " +
                            "FROM auth_users_api_keys AS k JOIN auth_users AS u ON u.uuid = k.user_id " +
                            "WHERE " + userScope + " AND k.last_activity >= :from AND k.last_activity <= :to " +
                            "ORDER BY k.last_activity DESC")
                    .bind("from", ctx.window().from())
                    .bind("to", ctx.window().to());
            binder.accept(q);
            return q.map((rs, c) -> BriefingItems.sample(rs.getString("key_name"), rs.getString("email"),
                            new DateTime(rs.getTimestamp("last_activity")), null, null))
                    .list();
        });

        return new AccessData(users.size(), users.subList(0, Math.min(users.size(), MAX_SAMPLES)), sessions,
                keys.size(), keys.subList(0, Math.min(keys.size(), MAX_SAMPLES)), authenticationEvents);
    }

    private EventCategoryCounts eventCategoryCounts(@Nullable UUID organizationId, SystemEventCategory category) {
        Map<String, Long> current = countEventsByReference(ctx.window().from(), ctx.window().to(), organizationId);
        Map<String, Long> previous = countEventsByReference(ctx.window().previousFrom(), ctx.window().previousTo(), organizationId);

        long currentTotal = 0;
        List<BriefingBreakdownEntryResponse> breakdown = Lists.newArrayList();
        for (Map.Entry<String, Long> entry : current.entrySet()) {
            if (categoryOf(entry.getKey()) != category) {
                continue;
            }
            currentTotal += entry.getValue();
            breakdown.add(BriefingItems.breakdownEntry(eventTypeName(entry.getKey(), category), entry.getValue()));
        }
        long previousTotal = previous.entrySet().stream()
                .filter(e -> categoryOf(e.getKey()) == category)
                .mapToLong(Map.Entry::getValue)
                .sum();
        breakdown.sort(Comparator.comparing(BriefingBreakdownEntryResponse::count).reversed()
                .thenComparing(BriefingBreakdownEntryResponse::label));

        return new EventCategoryCounts(currentTotal, previousTotal, foldBreakdown(breakdown));
    }

    private Map<String, Long> countEventsByReference(DateTime from, DateTime to, @Nullable UUID organizationId) {
        Map<String, Long> counts = Maps.newHashMap();
        ctx.nzyme().getDatabase().useHandle(handle -> {
            Query q = handle.createQuery("SELECT reference, COUNT(*) AS count FROM events WHERE event_type = 'SYSTEM' " +
                            "AND created_at >= :from AND created_at <= :to " +
                            (organizationId == null ? "AND organization_id IS NULL " : "AND organization_id = :organization_id ") +
                            "GROUP BY reference")
                    .bind("from", from)
                    .bind("to", to);
            if (organizationId != null) {
                q.bind("organization_id", organizationId);
            }
            q.map((rs, c) -> Map.entry(rs.getString("reference"), rs.getLong("count")))
                    .forEach(e -> counts.put(e.getKey(), e.getValue()));
        });
        return counts;
    }

    private static List<BriefingBreakdownEntryResponse> foldBreakdown(List<BriefingBreakdownEntryResponse> all) {
        if (all.size() <= MAX_BREAKDOWN_ENTRIES) {
            return all;
        }

        List<BriefingBreakdownEntryResponse> top = Lists.newArrayList(all.subList(0, MAX_BREAKDOWN_ENTRIES - 1));
        List<BriefingBreakdownEntryResponse> rest = all.subList(MAX_BREAKDOWN_ENTRIES - 1, all.size());
        top.add(BriefingItems.breakdownEntry(rest.size() + " other " + plural(rest.size(), "type", "types"),
                rest.stream().mapToLong(BriefingBreakdownEntryResponse::count).sum()));

        return top;
    }

    public @Nullable BriefingChangeGroupResponse accessGroup() {
        if (organizationAccess == null) {
            return null;
        }

        BriefingLinkResponse organization = BriefingLinks.to("ORGANIZATION", "uuid", ctx.organizationId().toString());
        AccessData a = organizationAccess;

        List<BriefingChangeTileResponse> tiles = Lists.newArrayList();
        tiles.add(BriefingItems.tile("logins", "Users who logged in", a.logins(), null, null, null,
                organization, a.loginSamples()));
        tiles.add(BriefingItems.tile("sessions", "Active user sessions", a.sessions(), null, null, null,
                organization, List.of()));
        tiles.add(BriefingItems.tile("api_keys", "API keys used", a.apiKeysUsed(), null, null, null,
                organization, a.apiKeySamples()));
        tiles.add(BriefingItems.breakdownTile("auth_events", "Authentication events", a.authenticationEvents().current(),
                a.authenticationEvents().previous(), null, organization, a.authenticationEvents().breakdown()));

        return BriefingItems.group("access", "Users and access", null, organization, tiles,
                "User accounts of the selected organization, across all of its tenants: who logged in, their open " +
                        "sessions, API keys that were used and authentication events. Super administrators are not " +
                        "included here.");
    }

    public @Nullable BriefingChangeGroupResponse clusterGroup() {
        if (superAdministratorAccess == null) {
            return null;
        }

        AccessData a = superAdministratorAccess;

        List<BriefingChangeTileResponse> tiles = Lists.newArrayList();
        tiles.add(BriefingItems.tile("superadmin_logins", "Super administrators who logged in", a.logins(), null, null, null,
                BriefingLinks.to("SESSIONS"), a.loginSamples()));
        tiles.add(BriefingItems.tile("superadmin_sessions", "Super administrator sessions", a.sessions(), null, null, null,
                BriefingLinks.to("SESSIONS"), List.of()));
        tiles.add(BriefingItems.tile("superadmin_api_keys", "Super administrator API keys used", a.apiKeysUsed(), null, null, null,
                BriefingLinks.to("SESSIONS"), a.apiKeySamples()));
        tiles.add(BriefingItems.breakdownTile("superadmin_events", "Super administrator events", a.authenticationEvents().current(),
                a.authenticationEvents().previous(), null, BriefingLinks.to("SYSTEM_EVENTS"), a.authenticationEvents().breakdown()));
        tiles.add(BriefingItems.breakdownTile("health_events", "Health indicator changes", healthEvents.current(),
                healthEvents.previous(), true, BriefingLinks.to("SYSTEM_EVENTS"), healthEvents.breakdown()));

        return BriefingItems.group("cluster", "Cluster", null, BriefingLinks.to("CLUSTER"), tiles,
                "Cluster-wide and independent of the selected organization: super administrator accounts and " +
                        "system health indicators.");
    }

    private static SystemEventCategory categoryOf(String reference) {
        try {
            return SystemEventType.valueOf(reference).getCategory();
        } catch (IllegalArgumentException e) {
            return SystemEventCategory.HEALTH_INDICATOR;
        }
    }

    // Health indicator event names are '"<indicator>" Health Indicator was toggled'.
    private static String eventTypeName(String reference, SystemEventCategory category) {
        String name;
        try {
            name = SystemEventType.valueOf(reference).getHumanReadableName();
        } catch (IllegalArgumentException e) {
            String lower = reference.toLowerCase().replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        if (category == SystemEventCategory.HEALTH_INDICATOR) {
            int open = name.indexOf('"');
            int close = name.indexOf('"', open + 1);
            if (open >= 0 && close > open) {
                return name.substring(open + 1, close);
            }
        }

        return name;
    }

    public List<BriefingAttentionItemResponse> attention() {
        List<BriefingAttentionItemResponse> items = Lists.newArrayList();

        if (healthIndicators == null) {
            return items;
        }

        for (Map.Entry<String, String> indicator : healthIndicators.entrySet()) {
            BriefingSeverity severity = switch (indicator.getValue()) {
                case "RED" -> BriefingSeverity.RED;
                case "ORANGE" -> BriefingSeverity.ORANGE;
                default -> null;
            };

            if (severity == null) {
                continue;
            }

            items.add(BriefingItems.attention(severity, "health", null,
                    "Health indicator \"" + indicatorName(indicator.getKey()) + "\" is " + indicator.getValue().toLowerCase(),
                    indicatorDescription(indicator.getKey()), 1, null, BriefingLinks.to("HEALTH")));
        }

        return items;
    }

    private static String indicatorDescription(String id) {
        return switch (id) {
            case "crypto_sync" -> "The PGP keys of the cluster nodes are not in sync.";
            case "db_clock" -> "The database clock deviates from the node clocks.";
            case "node_clock" -> "A node clock deviates from the database clock.";
            case "tap_clock" -> "A tap clock deviates from the node clock.";
            case "node_offline" -> "A cluster node stopped responding.";
            case "tls_exp" -> "A TLS certificate of a node is about to expire or has expired.";
            case "tap_offline" -> "A tap stopped reporting.";
            case "tap_tpx" -> "A tap reports no throughput.";
            case "tap_drop" -> "A tap is dropping frames.";
            case "tap_buffer" -> "A tap's processing buffers are filling up.";
            case "tap_error" -> "A tap reports processing errors.";
            case "tasks_queue_task_stuck" -> "A task in the tasks queue is stuck.";
            case "tasks_queue_task_failure" -> "A task in the tasks queue failed.";
            case "message_bus_message_failure" -> "A message on the message bus failed to process.";
            case "message_bus_message_stuck" -> "A message on the message bus is stuck.";
            case "tap_file_permissions" -> "A tap has files with unsafe permissions.";
            case "node_file_permissions" -> "A node has files with unsafe permissions.";
            case "connect" -> "Connect is enabled but the cluster cannot reach it. Data from Connect is not updated.";
            default -> "See the health page for details.";
        };
    }

    public List<BriefingCheckResponse> checks() {
        List<BriefingCheckResponse> checks = Lists.newArrayList();

        if (nodes != null && !nodes.isEmpty()) {
            long online = nodes.stream().filter(NodeState::online).count();
            checks.add(BriefingItems.check("nodes", online == nodes.size() ? "OK" : "WARN",
                    online + " of " + nodes.size() + " cluster " + plural(nodes.size(), "node", "nodes") + " online",
                    online == nodes.size() ? null : "Offline: " + String.join(", ",
                            nodes.stream().filter(n -> !n.online()).map(NodeState::name).toList()) + ".",
                    BriefingLinks.to("CLUSTER")));
        }

        if (ctx.isOrganizationAdministratorOrAbove()) {
            if (detectionSubscriptions == 0) {
                checks.add(BriefingItems.warn("subscriptions", "Alerts are not delivered anywhere",
                        "No alert subscriptions exist for this organization. Alerts are only visible in this web interface.",
                        BriefingLinks.to("ALERT_SUBSCRIPTIONS")));
            } else {
                checks.add(BriefingItems.ok("subscriptions", detectionSubscriptions + " alert "
                        + BriefingItems.plural(detectionSubscriptions, "subscription delivers", "subscriptions deliver")
                        + " detections", null, BriefingLinks.to("ALERT_SUBSCRIPTIONS")));
            }
        }

        return checks;
    }

    private static String indicatorName(String id) {
        return switch (id) {
            case "crypto_sync" -> "Crypto Sync";
            case "db_clock" -> "DB Clock";
            case "node_clock" -> "Node Clock";
            case "tap_clock" -> "Tap Clock";
            case "node_offline" -> "Node Offline";
            case "tls_exp" -> "TLS Expiration";
            case "tap_offline" -> "Tap Offline";
            case "tap_tpx" -> "Tap Throughput";
            case "tap_drop" -> "Tap Drop";
            case "tap_buffer" -> "Tap Buffer";
            case "tap_error" -> "Tap Error";
            case "tasks_queue_task_stuck" -> "Task Stuck";
            case "tasks_queue_task_failure" -> "Task Failure";
            case "message_bus_message_failure" -> "Message Failure";
            case "message_bus_message_stuck" -> "Message Stuck";
            case "tap_file_permissions" -> "Tap File Permissions";
            case "node_file_permissions" -> "Node File Permissions";
            case "connect" -> "Connect";
            default -> id;
        };
    }

}
