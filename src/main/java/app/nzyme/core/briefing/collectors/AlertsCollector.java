package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingItems;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.briefing.BriefingSeverity;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.detection.alerts.db.DetectionAlertAttributeEntry;
import app.nzyme.core.detection.alerts.db.DetectionAlertEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.responses.briefing.*;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.util.*;
import java.util.function.Predicate;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class AlertsCollector {

    private final BriefingContext ctx;
    private final @Nullable Subsystem subsystem;

    private List<DetectionAlertEntry> newAlerts;
    private long newAlertsPrevious;
    private List<DetectionAlertEntry> reactivatedAlerts;
    private long activeAlerts;
    private long activeAlertsOlder;

    public AlertsCollector(BriefingContext ctx, @Nullable Subsystem subsystem) {
        this.ctx = ctx;
        this.subsystem = subsystem;
    }

    public AlertsCollector load() {
        this.newAlerts = findNewAlerts(ctx.window().from(), ctx.window().to());
        this.newAlertsPrevious = countNewAlerts(ctx.window().previousFrom(), ctx.window().previousTo());
        this.reactivatedAlerts = findReactivatedAlerts();
        this.activeAlerts = countActiveAlerts(null);
        this.activeAlertsOlder = countActiveAlerts(ctx.window().from());

        return this;
    }

    public List<DetectionAlertEntry> newAlerts() {
        return newAlerts;
    }

    public long newAlertsCount() {
        return newAlerts.size();
    }

    public long newAlertsPreviousCount() {
        return newAlertsPrevious;
    }

    public List<DetectionAlertEntry> reactivatedAlerts() {
        return reactivatedAlerts;
    }

    public long activeAlertsCount() {
        return activeAlerts;
    }

    public long countNewAlertsOfTypes(Set<DetectionType> types) {
        return newAlerts.stream().filter(a -> types.contains(typeOf(a))).count();
    }

    public long countNewAlertsOfTypesPrevious(Set<DetectionType> types) {
        return countNewAlertsOfTypes(ctx.window().previousFrom(), ctx.window().previousTo(), types);
    }

    // Types matched by skip are left out so a subsystem collector can describe them itself.
    public List<BriefingAttentionItemResponse> attention(Predicate<DetectionType> skip) {
        List<BriefingAttentionItemResponse> items = Lists.newArrayList();

        Map<DetectionType, List<DetectionAlertEntry>> byType = new EnumMap<>(DetectionType.class);
        for (DetectionAlertEntry alert : newAlerts) {
            DetectionType type = typeOf(alert);
            if (type == null || skip.test(type)) {
                continue;
            }
            byType.computeIfAbsent(type, k -> Lists.newArrayList()).add(alert);
        }

        for (Map.Entry<DetectionType, List<DetectionAlertEntry>> entry : byType.entrySet()) {
            DetectionType type = entry.getKey();
            List<DetectionAlertEntry> alerts = entry.getValue();
            DateTime latest = alerts.stream().map(DetectionAlertEntry::createdAt).max(DateTime::compareTo).orElse(null);
            String subsystemName = type.getSubsystem().name();

            if (alerts.size() == 1) {
                DetectionAlertEntry alert = alerts.get(0);
                items.add(BriefingItems.attention(BriefingSeverity.ALERT, "alert", subsystemName,
                        type.getTitle(), describe(alert), 1, latest, BriefingLinks.alert(alert.uuid().toString())));
            } else {
                String text = alerts.size() + " new alerts of this type. Latest: " + describe(alerts.get(0));
                items.add(BriefingItems.attention(BriefingSeverity.ALERT, "alert", subsystemName,
                        type.getTitle() + ", " + alerts.size() + " times", text, alerts.size(), latest,
                        BriefingLinks.alerts(subsystemName, type.name(), null)));
            }
        }

        if (!reactivatedAlerts.isEmpty()) {
            long count = reactivatedAlerts.size();
            DetectionAlertEntry sample = reactivatedAlerts.get(0);
            items.add(BriefingItems.attention(BriefingSeverity.ALERT, "alert",
                    subsystem == null ? null : subsystem.name(),
                    count + " " + plural(count, "alert", "alerts") + " flared up again",
                    count == 1 ? "Known alert that went quiet and came back: " + describe(sample)
                            : "Known alerts that went quiet and came back. For example: " + describe(sample),
                    count, sample.lastSeen(),
                    count == 1 ? BriefingLinks.alert(sample.uuid().toString())
                            : BriefingLinks.alerts(subsystem == null ? null : subsystem.name(), null, "Active")));
        }

        if (activeAlertsOlder > 0) {
            items.add(BriefingItems.attention(BriefingSeverity.ALERT, "alert",
                    subsystem == null ? null : subsystem.name(),
                    activeAlertsOlder + " older " + plural(activeAlertsOlder, "alert is", "alerts are") + " still active",
                    "Raised before this briefing window and still being observed right now.",
                    activeAlertsOlder, null,
                    BriefingLinks.alerts(subsystem == null ? null : subsystem.name(), null, "Active")));
        }

        return items;
    }

    public static @Nullable DetectionType typeOf(DetectionAlertEntry alert) {
        try {
            return DetectionType.valueOf(alert.detectionType());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String describe(DetectionAlertEntry alert) {
        StringBuilder sb = new StringBuilder();

        if (alert.dot11MonitoredNetworkId() != null) {
            Optional<MonitoredSSID> network = ctx.nzyme().getDot11().findMonitoredSSID(alert.dot11MonitoredNetworkId());
            network.ifPresent(n -> sb.append("Network \"").append(n.ssid()).append("\": "));
        }

        sb.append(alert.details() == null ? "" : alert.details().trim());

        return sb.toString();
    }

    public Map<String, String> attributesOf(DetectionAlertEntry alert) {
        Map<String, String> attributes = Maps.newHashMap();
        for (DetectionAlertAttributeEntry attr : ctx.nzyme().getDetectionAlertService().findAlertAttributes(alert.id())) {
            attributes.put(attr.key(), attr.value());
        }
        return attributes;
    }

    private List<DetectionAlertEntry> findNewAlerts(DateTime from, DateTime to) {
        String subsystemSql = subsystem == null ? "" : "AND subsystem = :subsystem ";

        return ctx.nzyme().getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT * FROM detection_alerts " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND created_at >= :from AND created_at <= :to " + subsystemSql +
                            "ORDER BY created_at DESC LIMIT 500")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .bind("from", from)
                    .bind("to", to);
            if (subsystem != null) {
                query.bind("subsystem", subsystem.name());
            }
            return query.mapTo(DetectionAlertEntry.class).list();
        });
    }

    private long countNewAlerts(DateTime from, DateTime to) {
        String subsystemSql = subsystem == null ? "" : "AND subsystem = :subsystem ";

        return ctx.nzyme().getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND created_at >= :from AND created_at <= :to " + subsystemSql)
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .bind("from", from)
                    .bind("to", to);
            if (subsystem != null) {
                query.bind("subsystem", subsystem.name());
            }
            return query.mapTo(Long.class).one();
        });
    }

    private long countNewAlertsOfTypes(DateTime from, DateTime to, Set<DetectionType> types) {
        if (types.isEmpty()) {
            return 0;
        }

        List<String> names = types.stream().map(Enum::name).toList();

        return ctx.nzyme().getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND created_at >= :from AND created_at <= :to AND detection_type IN (<types>)")
                        .bind("organization_id", ctx.organizationId())
                        .bind("tenant_id", ctx.tenantId())
                        .bind("from", from)
                        .bind("to", to)
                        .bindList("types", names)
                        .mapTo(Long.class)
                        .one()
        );
    }

    // Raised before the window, new timeline period inside it.
    private List<DetectionAlertEntry> findReactivatedAlerts() {
        String subsystemSql = subsystem == null ? "" : "AND da.subsystem = :subsystem ";

        return ctx.nzyme().getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT DISTINCT ON (da.id) da.* FROM detection_alerts AS da " +
                            "JOIN detection_alert_timeline AS t ON t.detection_alert_id = da.id " +
                            "WHERE da.organization_id = :organization_id AND da.tenant_id = :tenant_id " +
                            "AND da.created_at < :from AND t.seen_from >= :from AND t.seen_from <= :to " +
                            subsystemSql + "ORDER BY da.id, da.last_seen DESC LIMIT 200")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .bind("from", ctx.window().from())
                    .bind("to", ctx.window().to());
            if (subsystem != null) {
                query.bind("subsystem", subsystem.name());
            }
            return query.mapTo(DetectionAlertEntry.class).list();
        });
    }

    // With a cutoff only alerts created before it count.
    private long countActiveAlerts(@Nullable DateTime createdBefore) {
        String subsystemSql = subsystem == null ? "" : "AND subsystem = :subsystem ";
        String createdSql = createdBefore == null ? "" : "AND created_at < :created_before ";

        return ctx.nzyme().getDatabase().withHandle(handle -> {
            Query query = handle.createQuery("SELECT COUNT(*) FROM detection_alerts " +
                            "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                            "AND is_resolved = false AND last_seen > :cutoff " + subsystemSql + createdSql)
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .bind("cutoff", DateTime.now().minusMinutes(DetectionAlertService.ACTIVE_THRESHOLD_MINUTES));
            if (subsystem != null) {
                query.bind("subsystem", subsystem.name());
            }
            if (createdBefore != null) {
                query.bind("created_before", createdBefore);
            }
            return query.mapTo(Long.class).one();
        });
    }

}
