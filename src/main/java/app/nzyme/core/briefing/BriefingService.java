package app.nzyme.core.briefing;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.briefing.collectors.*;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.briefing.*;
import app.nzyme.core.security.authentication.AuthenticationService;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.*;

public class BriefingService {

    public static final int MAX_WINDOW_DAYS = 7;

    public static final int MIN_WINDOW_MINUTES = 15;

    private static final int DEFAULT_WINDOW_HOURS = 24;

    private final NzymeNode nzyme;
    private final BriefingVisits visits;

    public BriefingService(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.visits = new BriefingVisits(nzyme);
    }

    public BriefingVisits visits() {
        return visits;
    }

    public BriefingWindow resolveWindow(BriefingType type,
                                        AuthenticatedUser user,
                                        BriefingSinceMode mode,
                                        @Nullable TimeRange timeRange) {
        Optional<DateTime> lastVisit = visits.touch(user.getUserId(), type);
        AuthenticationService.UserLoginTimestamps logins = nzyme.getAuthenticationService()
                .findLoginTimestampsOfUser(user.getUserId());

        DateTime now = DateTime.now();
        DateTime from;
        DateTime to = now;
        boolean firstVisit = false;
        boolean capped = false;

        switch (mode) {
            case LAST_VISIT -> {
                if (lastVisit.isPresent()) {
                    from = lastVisit.get();
                } else {
                    from = now.minusHours(DEFAULT_WINDOW_HOURS);
                    firstVisit = true;
                }
            }
            case LAST_SESSION -> {
                // Users who logged in before session ends were recorded only have previous_login_at.
                DateTime sessionEnd = logins.previousSessionEndedAt() != null
                        ? logins.previousSessionEndedAt() : logins.previousLoginAt();
                if (sessionEnd != null) {
                    from = sessionEnd;
                } else {
                    from = now.minusHours(DEFAULT_WINDOW_HOURS);
                    firstVisit = true;
                }
            }
            case TIME_RANGE -> {
                if (timeRange == null || timeRange.isAllTime()) {
                    from = now.minusHours(DEFAULT_WINDOW_HOURS);
                } else {
                    from = timeRange.from();
                    to = timeRange.to();
                }
            }
            default -> throw new IllegalStateException("Unknown since mode.");
        }

        if (mode != BriefingSinceMode.TIME_RANGE) {
            if (from.isBefore(now.minusDays(MAX_WINDOW_DAYS))) {
                from = now.minusDays(MAX_WINDOW_DAYS);
                capped = true;
            }
            if (from.isAfter(now.minusMinutes(MIN_WINDOW_MINUTES))) {
                from = now.minusMinutes(MIN_WINDOW_MINUTES);
            }
        } else if (new Duration(from, to).getStandardDays() > MAX_WINDOW_DAYS * 4L) {
            from = to.minusDays(MAX_WINDOW_DAYS * 4);
            capped = true;
        }

        return new BriefingWindow(mode, from, to, firstVisit, capped,
                lastVisit.orElse(null), logins.lastLoginAt(),
                logins.previousSessionEndedAt() != null ? logins.previousSessionEndedAt() : logins.previousLoginAt());
    }

    public BriefingContext buildContext(AuthenticatedUser user,
                                        UUID organizationId,
                                        UUID tenantId,
                                        List<UUID> taps,
                                        BriefingWindow window,
                                        boolean canViewAlerts,
                                        boolean canManageDot11Monitoring,
                                        boolean canManageEthernetMonitoring,
                                        boolean canManageBluetoothMonitoring) {
        Set<Subsystem> subsystems = Sets.newHashSet();
        for (Subsystem subsystem : List.of(Subsystem.DOT11, Subsystem.ETHERNET, Subsystem.BLUETOOTH, Subsystem.UAV)) {
            if (nzyme.getSubsystems().isEnabled(subsystem, organizationId, tenantId)) {
                subsystems.add(subsystem);
            }
        }

        return new BriefingContext(nzyme, organizationId, tenantId, taps, window, canViewAlerts,
                canManageDot11Monitoring, canManageEthernetMonitoring, canManageBluetoothMonitoring,
                user.isOrganizationAdministrator(), user.isSuperAdministrator(), subsystems);
    }

    public BriefingResponse build(BriefingType type, BriefingContext ctx) {
        return switch (type) {
            case MAIN -> buildMain(ctx);
            case DOT11 -> buildDot11(ctx);
            case ETHERNET -> buildEthernet(ctx);
            case BLUETOOTH -> buildBluetooth(ctx);
        };
    }

    private BriefingResponse buildMain(BriefingContext ctx) {
        TapsCollector taps = new TapsCollector(ctx).load();
        SystemCollector system = new SystemCollector(ctx).load();

        List<BriefingAttentionItemResponse> attention = Lists.newArrayList();
        List<BriefingCheckResponse> checks = Lists.newArrayList();
        List<BriefingChangeGroupResponse> changes = Lists.newArrayList();
        Map<String, Object> activity = Maps.newHashMap();

        attention.addAll(taps.attention());
        attention.addAll(system.attention());
        checks.addAll(taps.checks());
        checks.addAll(system.checks());

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(ctx.window().combined());
        activity.put("throughput", taps.throughputHistogram(ctx.window().combined(), bucketing.type().getDateTruncName()));

        if (ctx.canViewAlerts() && ctx.subsystems().isEmpty()) {
            attention.addAll(new AlertsCollector(ctx, null).load().attention(t -> false));
        }

        BriefingChangeGroupResponse accessGroup = system.accessGroup();
        if (accessGroup != null) {
            changes.add(accessGroup);
        }
        BriefingChangeGroupResponse clusterGroup = system.clusterGroup();
        if (clusterGroup != null) {
            changes.add(clusterGroup);
        }

        // Coverage checks of every enabled subsystem are always shown. Subsystems are enabled by default, so attention
        // items and charts only appear once a tap captures the subsystem.
        if (ctx.hasSubsystem(Subsystem.DOT11)) {
            AlertsCollector dot11Alerts = new AlertsCollector(ctx, Subsystem.DOT11).load();
            Dot11Collector dot11 = new Dot11Collector(ctx, dot11Alerts).load();
            boolean inUse = taps.capturesType("WiFi");
            checks.addAll(tagged(dot11.checks(taps), Subsystem.DOT11));
            if (ctx.canViewAlerts()) {
                // Without the monitoring permission there are no per-network items, so the alerts stay here.
                boolean perNetwork = inUse && ctx.canManageDot11Monitoring();
                attention.addAll(dot11Alerts.attention(perNetwork ? Dot11Collector.NETWORK_ALERT_TYPES::contains : t -> false));
            }
            if (inUse) {
                attention.addAll(dot11.attention());
                activity.put("dot11", dot11.activity());
            }
        }

        if (ctx.hasSubsystem(Subsystem.ETHERNET)) {
            AlertsCollector ethernetAlerts = new AlertsCollector(ctx, Subsystem.ETHERNET).load();
            EthernetCollector ethernet = new EthernetCollector(ctx, ethernetAlerts).load();
            checks.addAll(tagged(ethernet.checks(taps), Subsystem.ETHERNET));
            if (ctx.canViewAlerts()) {
                attention.addAll(ethernetAlerts.attention(t -> false));
            }
            if (taps.capturesType("Ethernet")) {
                attention.addAll(ethernet.attention());
                activity.put("ethernet", ethernet.activity());
            }
        }

        if (ctx.hasSubsystem(Subsystem.BLUETOOTH)) {
            BluetoothCollector bluetooth = new BluetoothCollector(ctx).load();
            checks.addAll(tagged(bluetooth.checks(), Subsystem.BLUETOOTH));
            if (ctx.canViewAlerts()) {
                attention.addAll(new AlertsCollector(ctx, Subsystem.BLUETOOTH).load().attention(t -> false));
            }
            if (bluetooth.inUse(taps)) {
                activity.put("bluetooth", bluetooth.activity());
                changes.add(bluetooth.devicesGroup());
            }
        }
        if (ctx.hasSubsystem(Subsystem.UAV) && ctx.canViewAlerts()) {
            attention.addAll(new AlertsCollector(ctx, Subsystem.UAV).load().attention(t -> false));
        }

        return assemble(BriefingType.MAIN, ctx, attention, checks, changes, activity, taps, system,
                new LocationsCollector(ctx, null).load().locations());
    }

    private static List<BriefingCheckResponse> tagged(List<BriefingCheckResponse> checks, Subsystem subsystem) {
        return checks.stream().map(c -> c.withSubsystem(subsystem.name())).toList();
    }

    private BriefingResponse buildDot11(BriefingContext ctx) {
        TapsCollector taps = new TapsCollector(ctx).load();
        AlertsCollector alerts = new AlertsCollector(ctx, Subsystem.DOT11).load();
        Dot11Collector dot11 = new Dot11Collector(ctx, alerts).load();

        List<BriefingAttentionItemResponse> attention = Lists.newArrayList();
        attention.addAll(taps.attention());
        if (ctx.canViewAlerts()) {
            attention.addAll(alerts.attention(ctx.canManageDot11Monitoring()
                    ? Dot11Collector.NETWORK_ALERT_TYPES::contains : t -> false));
        }
        attention.addAll(dot11.attention());

        List<BriefingCheckResponse> checks = Lists.newArrayList();
        checks.addAll(taps.checks());
        checks.addAll(dot11.checks(taps));

        return assemble(BriefingType.DOT11, ctx, attention, checks, List.of(), dot11.activity(), taps, null,
                new LocationsCollector(ctx, Subsystem.DOT11).load().locations());
    }

    private BriefingResponse buildEthernet(BriefingContext ctx) {
        TapsCollector taps = new TapsCollector(ctx).load();
        AlertsCollector alerts = new AlertsCollector(ctx, Subsystem.ETHERNET).load();
        EthernetCollector ethernet = new EthernetCollector(ctx, alerts).load();

        List<BriefingAttentionItemResponse> attention = Lists.newArrayList();
        attention.addAll(taps.attention());
        if (ctx.canViewAlerts()) {
            attention.addAll(alerts.attention(t -> false));
        }
        attention.addAll(ethernet.attention());

        List<BriefingCheckResponse> checks = Lists.newArrayList();
        checks.addAll(taps.checks());
        checks.addAll(ethernet.checks(taps));

        return assemble(BriefingType.ETHERNET, ctx, attention, checks, List.of(), ethernet.activity(), taps, null,
                new LocationsCollector(ctx, Subsystem.ETHERNET).load().locations());
    }

    private BriefingResponse buildBluetooth(BriefingContext ctx) {
        TapsCollector taps = new TapsCollector(ctx).load();
        AlertsCollector alerts = new AlertsCollector(ctx, Subsystem.BLUETOOTH).load();
        BluetoothCollector bluetooth = new BluetoothCollector(ctx).load();

        List<BriefingAttentionItemResponse> attention = Lists.newArrayList();
        attention.addAll(taps.attention());
        if (ctx.canViewAlerts()) {
            attention.addAll(alerts.attention(t -> false));
        }

        List<BriefingCheckResponse> checks = Lists.newArrayList();
        checks.addAll(taps.checks());
        checks.addAll(bluetooth.checks());

        return assemble(BriefingType.BLUETOOTH, ctx, attention, checks, List.of(bluetooth.devicesGroup()),
                bluetooth.activity(), taps, null, new LocationsCollector(ctx, Subsystem.BLUETOOTH).load().locations());
    }

    private BriefingResponse assemble(BriefingType type,
                                      BriefingContext ctx,
                                      List<BriefingAttentionItemResponse> attention,
                                      List<BriefingCheckResponse> checks,
                                      List<BriefingChangeGroupResponse> changes,
                                      Map<String, Object> activity,
                                      TapsCollector taps,
                                      @Nullable SystemCollector system,
                                      List<BriefingLocationResponse> locations) {
        attention.sort(Comparator
                .comparing((BriefingAttentionItemResponse a) -> BriefingSeverity.valueOf(a.severity()).ordinal())
                .thenComparing(a -> a.timestamp() == null ? 0L : -a.timestamp().getMillis()));

        checks.sort(Comparator.comparing(c -> switch (c.level()) {
            case "WARN" -> 0;
            case "INFO" -> 1;
            default -> 2;
        }));

        BriefingWindow window = ctx.window();
        BriefingWindowResponse windowResponse = BriefingWindowResponse.create(
                window.mode().name(), window.from(), window.to(), window.previousFrom(), window.previousTo(),
                window.duration().getMillis(), window.firstVisit(), window.capped(),
                window.lastVisitAt(), window.lastLoginAt(), window.previousSessionEndedAt()
        );

        BriefingMonitoringResponse monitoring = BriefingMonitoringResponse.create(
                taps.taps(), taps.online(), taps.total(), checks,
                system == null ? null : system.healthIndicators()
        );

        return BriefingResponse.create(
                type.name(), DateTime.now(), windowResponse, attention, monitoring, changes, activity,
                ctx.subsystems().stream().map(Enum::name).sorted().toList(), locations
        );
    }

}
