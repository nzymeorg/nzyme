package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.detection.alerts.DetectionAlertService;
import app.nzyme.core.environment.EnvironmentService;
import app.nzyme.core.environment.dto.EnvironmentData;
import app.nzyme.core.environment.dto.LocationEnvironmentAlertDetails;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.rest.responses.briefing.BriefingLocationEnvironmentResponse;
import app.nzyme.core.rest.responses.briefing.BriefingLocationResponse;
import app.nzyme.core.rest.responses.locations.LocationEnvironmentConditionDetailsResponse;
import app.nzyme.plugin.Subsystem;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Tools;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.statement.Query;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LocationsCollector {

    private final BriefingContext ctx;
    private final @Nullable Subsystem subsystem;

    private List<BriefingLocationResponse> locations;

    public LocationsCollector(BriefingContext ctx, @Nullable Subsystem subsystem) {
        this.ctx = ctx;
        this.subsystem = subsystem;
    }

    public LocationsCollector load() {
        locations = Lists.newArrayList();

        for (TenantLocationEntry location : ctx.nzyme().getAuthenticationService()
                .findAllTenantLocations(ctx.organizationId(), ctx.tenantId(), Integer.MAX_VALUE, 0)) {
            // Only the taps the user may access, unlike the Locations page.
            List<Tap> taps = ctx.nzyme().getTapManager().findAllTapsAtLocation(location.uuid()).stream()
                    .filter(t -> ctx.taps().contains(t.uuid()))
                    .toList();

            List<String> offline = taps.stream()
                    .filter(t -> !Tools.isTapActive(t.lastReport()))
                    .map(Tap::name)
                    .sorted()
                    .toList();

            Long alertCount = ctx.canViewAlerts() ? countActiveAlerts(taps.stream().map(Tap::uuid).toList()) : null;

            locations.add(BriefingLocationResponse.create(
                    location.uuid(), location.name(), taps.size(), taps.size() - offline.size(), offline,
                    alertCount, environment(location), BriefingLinks.to("LOCATION", "uuid", location.uuid().toString())
            ));
        }

        return this;
    }

    public List<BriefingLocationResponse> locations() {
        return locations;
    }

    // Same "active" rule as the Locations page.
    private long countActiveAlerts(List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return ctx.nzyme().getDatabase().withHandle(handle -> {
            Query q = handle.createQuery("SELECT COUNT(*) FROM detection_alerts WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND is_resolved = false AND last_seen > :cutoff " +
                            "AND tap_id IN (<taps>) " + (subsystem == null ? "" : "AND subsystem = :subsystem"))
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .bind("cutoff", DateTime.now().minusMinutes(DetectionAlertService.ACTIVE_THRESHOLD_MINUTES))
                    .bindList("taps", taps);
            if (subsystem != null) {
                q.bind("subsystem", subsystem.name());
            }
            return q.mapTo(Long.class).one();
        });
    }

    private @Nullable BriefingLocationEnvironmentResponse environment(TenantLocationEntry location) {
        if (location.latitude() == null || location.longitude() == null) {
            return null;
        }

        Optional<EnvironmentData> data = ctx.nzyme().getEnvironmentService().getEnvironmentData(location.uuid());
        if (data.isEmpty()) {
            return null;
        }

        EnvironmentData e = data.get();

        List<String> alerts = Lists.newArrayList();
        for (LocationEnvironmentAlertDetails a : e.alerts()) {
            if (a.severity() != null && EnvironmentService.severityOrdinal(a.severity()) < EnvironmentService.MIN_ALERT_SEVERITY) {
                continue;
            }
            if (!EnvironmentService.alertIsCurrentlyRelevant(a)) {
                continue;
            }
            alerts.add(a.event() == null ? "Unknown event" : a.event());
        }

        return BriefingLocationEnvironmentResponse.create(
                e.condition() == null ? null : LocationEnvironmentConditionDetailsResponse.create(
                        e.condition().displayName(), e.condition().severity()),
                e.temperature(), e.windSpeed(), e.windGust(), alerts
        );
    }

}
