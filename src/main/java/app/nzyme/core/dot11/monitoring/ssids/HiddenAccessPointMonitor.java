package app.nzyme.core.dot11.monitoring.ssids;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.dot11.AccessPointSiblings;
import app.nzyme.core.dot11.hidden.HiddenAccessPoints;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HiddenAccessPointMonitor extends DwellTimeMonitor {

    private static final Logger LOG = LogManager.getLogger(HiddenAccessPointMonitor.class);

    private static final int HINT_REFRESH_MINUTES = 60;
    private static final int HINT_BUDGET_MS = 5000;

    private final HiddenAccessPoints hidden;
    private final Timer runTimer;

    public HiddenAccessPointMonitor(NzymeNode nzyme) {
        super(nzyme);

        this.hidden = nzyme.getDot11().hiddenAccessPoints();
        this.runTimer = nzyme.getMetrics().timer(MetricNames.DOT11_HIDDEN_ACCESS_POINT_MONITOR_RUN_TIMER);
    }

    @Override
    public void runForTenant(UUID organizationId, UUID tenantId, int dwellTimeMinutes, boolean eventingEnabled) {
        try (Timer.Context ignored = runTimer.time()) {
            super.runForTenant(organizationId, tenantId, dwellTimeMinutes, eventingEnabled);
        }
    }

    @Override
    protected String subjects() {
        return "hidden access points";
    }

    @Override
    protected String name() {
        return "dot11_hidden_access_points";
    }

    // The names of a report are written after its BSSID. A report is read once it has them.
    @Override
    protected int settleSeconds() {
        return 30;
    }

    @Override
    protected List<String> findSeen(Handle handle, Tenant tenant, DateTime from, DateTime to, int limit) {
        return hidden.findSeenBetween(handle, tenant.taps(), from, to, limit)
                .stream()
                .filter(Tools::isValidMacAddress)
                .toList();
    }

    @Override
    protected Set<String> touchKnown(Handle handle, Tenant tenant, List<String> seen) {
        Set<String> known = Sets.newHashSet();
        hidden.findByBssids(handle, seen, tenant.organizationId(), tenant.tenantId())
                .forEach(entry -> known.add(entry.bssid()));
        hidden.touch(handle, tenant.organizationId(), tenant.tenantId(), known);

        return known;
    }

    @Override
    protected boolean hasRoomFor(Tenant tenant, int unknown) {
        if (hidden.count(tenant.organizationId(), tenant.tenantId()) < HiddenAccessPoints.MAX_ENTRIES_PER_TENANT) {
            return true;
        }

        LOG.warn("Tenant [{}/{}] has reached the limit of <{}> hidden access points. Not recording <{}> more.",
                tenant.organizationId(), tenant.tenantId(), HiddenAccessPoints.MAX_ENTRIES_PER_TENANT, unknown);

        return false;
    }

    @Override
    protected Optional<Sightings> findSightings(Handle handle, Tenant tenant, String bssid, DateTime from, DateTime to) {
        return hidden.findSightings(handle, tenant.taps(), bssid, from, to);
    }

    @Override
    protected boolean create(Handle handle, Tenant tenant, String bssid, List<UUID> alertTaps) {
        return hidden.create(handle, tenant.organizationId(), tenant.tenantId(), bssid, alertTaps);
    }

    @Override
    protected List<Pending> findPendingToEvaluate(Tenant tenant, DateTime evaluatedBefore, int limit) {
        return hidden.findPendingToEvaluate(tenant.organizationId(), tenant.tenantId(), tenant.dwellSince(),
                        evaluatedBefore, tenant.now(), limit)
                .stream()
                .map(entry -> new Pending(entry.id(), entry.bssid()))
                .toList();
    }

    @Override
    protected void setEvaluated(Handle handle, long id, DateTime at, @Nullable Collection<UUID> alertTaps) {
        hidden.setEvaluated(handle, id, at, alertTaps);
    }

    @Override
    protected void afterEvaluation(Tenant tenant) {
        refreshHints(tenant, System.currentTimeMillis() + HINT_BUDGET_MS);
    }

    @Override
    protected List<Alerting> findAlerting(Tenant tenant) {
        return hidden.findPendingWithAlertTaps(tenant.organizationId(), tenant.tenantId(), tenant.dwellSince())
                .stream()
                .map(entry -> {
                    // Attributes of an existing alert are only updated, never added. Both hints are always part of it.
                    Map<String, String> attributes = Maps.newHashMap();
                    attributes.put("bssid", entry.bssid());
                    attributes.put("sibling_ssids", String.join(", ", entry.siblingSsids()));
                    attributes.put("revealed_ssids", String.join(", ", entry.revealedSsids()));

                    return new Alerting(entry.bssid(), entry.alertTaps(), attributes);
                })
                .toList();
    }

    @Override
    protected void raiseAlert(Tenant tenant, Alerting alerting, List<UUID> taps) {
        nzyme.getDetectionAlertService().raiseAlert(
                tenant.organizationId(),
                tenant.tenantId(),
                null,
                AlertOrigin.taps(taps),
                DetectionType.DOT11_UNAPPROVED_HIDDEN_ACCESS_POINT,
                Subsystem.DOT11,
                "Unapproved hidden access point \"" + alerting.name() + "\" detected.",
                alerting.attributes(),
                Set.of("bssid")
        );
    }

    // The same lookup as on the access point pages, a batch of entries at a time.
    private void refreshHints(Tenant tenant, long deadline) {
        TimeRange timeRange = TimeRange.create(tenant.dwellSince(), tenant.now(), false);

        while (System.currentTimeMillis() <= deadline) {
            List<HiddenAccessPoints.Entry> entries = hidden.findWithHintsOlderThan(tenant.organizationId(),
                    tenant.tenantId(), tenant.now().minusMinutes(HINT_REFRESH_MINUTES),
                    AccessPointSiblings.MAX_ADDRESSES_PER_LOOKUP);

            if (entries.isEmpty()) {
                return;
            }

            List<Long> ids = entries.stream().map(HiddenAccessPoints.Entry::id).sorted().toList();
            List<String> bssids = entries.stream().map(HiddenAccessPoints.Entry::bssid).toList();

            Optional<Boolean> done;
            try {
                Map<String, AccessPointSiblings.Siblings> siblings = nzyme.getDot11().accessPointSiblings()
                        .find(bssids, timeRange, tenant.taps());

                done = locked(tenant, handle -> {
                    Map<String, List<String>> revealed = hidden.findRevealedSsids(handle, tenant.taps(), bssids,
                            tenant.dwellSince());

                    Map<Long, List<String>> siblingSsids = Maps.newHashMap();
                    Map<Long, List<String>> revealedSsids = Maps.newHashMap();
                    for (HiddenAccessPoints.Entry entry : entries) {
                        siblingSsids.put(entry.id(), siblings.containsKey(entry.bssid())
                                ? siblings.get(entry.bssid()).ssids() : List.of());
                        revealedSsids.put(entry.id(), revealed.getOrDefault(entry.bssid(), List.of()));
                    }

                    hidden.setHints(handle, ids, siblingSsids, revealedSsids);
                    return true;
                });
            } catch (Exception e) {
                LOG.warn("Could not refresh hints of <{}> hidden access points of tenant [{}/{}]. Keeping their " +
                        "hints.", entries.size(), tenant.organizationId(), tenant.tenantId(), e);

                // Marked as refreshed, or the same entries would be the first of every run.
                done = locked(tenant, handle -> {
                    hidden.setHints(handle, ids, null, null);
                    return true;
                });
            }

            if (done.isEmpty()) {
                return;
            }
        }
    }

}
