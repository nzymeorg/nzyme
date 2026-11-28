package app.nzyme.core.ethernet.portalintegrity;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.detection.alerts.AlertOrigin;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.ethernet.portalintegrity.db.PortalIntegrityControlUrlEntry;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.MetricNames;
import app.nzyme.plugin.Subsystem;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * Raises the alerts of monitored control URLs from what their last report showed. A probe reports every few
 * minutes at best, and an alert that is not raised for five minutes counts as over, so the alert is kept up
 * from here for as long as the last report is current. Once the reports stop, the probe is silent, unless
 * the tap says that it no longer runs it.
 */
public class PortalIntegrityMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(PortalIntegrityMonitor.class);

    private static final int WATCH_GAP_MINUTES = 3;

    private record SilentProbe(UUID tapUuid, String probeName, int ipVersion) {}

    private final NzymeNode nzyme;
    private final Timer runTimer;

    /*
     * Reports that did not arrive while no node watched a tenant (nodes down, subsystem disabled) say nothing
     * about its probes: taps do not send them again. Silence only counts from when the tenant is watched again.
     */
    private final Map<UUID, DateTime> lastWatched = Maps.newHashMap();
    private final Map<UUID, DateTime> watchedSince = Maps.newHashMap();

    public PortalIntegrityMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.runTimer = nzyme.getMetrics().timer(MetricNames.PORTAL_INTEGRITY_MONITOR_RUN_TIMER);
    }

    @Override
    protected void execute() {
        try (Timer.Context ignored = runTimer.time()) {
            for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
                try {
                    check(tenant);
                } catch (Exception e) {
                    LOG.error("Could not check monitored portal integrity control URLs of tenant [{}/{}].",
                            tenant.organizationUuid(), tenant.uuid(), e);
                }
            }
        }
    }

    private void check(TenantEntry tenant) {
        if (!nzyme.getSubsystems().isEnabled(Subsystem.ETHERNET, tenant.organizationUuid(), tenant.uuid())) {
            return;
        }

        nzyme.getDatabase().useTransaction(handle -> {
            // Only one node of a cluster checks a tenant at a time. The other one is watching it then.
            boolean locked = handle.createQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                    .bind("key", "portal_integrity_monitor:" + tenant.organizationUuid() + ":" + tenant.uuid())
                    .mapTo(Boolean.class)
                    .one();

            DateTime now = nzyme.getDatabase().getDatabaseClock();
            DateTime watching = watching(tenant.uuid(), now);

            if (locked) {
                check(tenant, now, watching);
            }
        });
    }

    private DateTime watching(UUID tenantId, DateTime now) {
        DateTime last = lastWatched.put(tenantId, now);
        if (last == null || last.plusMinutes(WATCH_GAP_MINUTES).isBefore(now)) {
            watchedSince.put(tenantId, now);
        }

        return watchedSince.get(tenantId);
    }

    private void check(TenantEntry tenant, DateTime now, DateTime watching) {
        PortalIntegrity portalIntegrity = nzyme.getEthernet().portalIntegrity();
        List<PortalIntegrityControlUrlEntry> entries = portalIntegrity
                .findAllMonitoredControlUrls(tenant.organizationUuid(), tenant.uuid());
        if (entries.isEmpty()) {
            return;
        }

        Map<UUID, String> tapNames = Maps.newHashMap();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(tenant.organizationUuid(), tenant.uuid())) {
            tapNames.put(tap.uuid(), tap.name());
        }
        PortalProbeConfigurations probes = portalIntegrity
                .findProbeConfigurations(tenant.organizationUuid(), tenant.uuid());

        Map<SilentProbe, List<PortalIntegrityControlUrlEntry>> silent = Maps.newLinkedHashMap();
        for (PortalIntegrityControlUrlEntry entry : entries) {
            try {
                PortalIntegrityJudge.Status status = PortalIntegrityJudge.status(entry, probes, now);

                DateTime since = entry.inTapConfigurationSince();
                if (Boolean.FALSE.equals(status.inTapConfiguration())) {
                    if (since != null) {
                        portalIntegrity.setInTapConfigurationSince(entry.id(), null);
                    }
                    since = null;
                } else if (since == null) {
                    portalIntegrity.setInTapConfigurationSince(entry.id(), now);
                    since = now;
                }

                /*
                 * A probe that was just put back into the tap configuration, a control URL that just got an
                 * expectation and a tenant that was not watched for a while get the time a probe has to report
                 * before it counts as silent.
                 */
                if (status.result() == PortalIntegrityResult.NOT_REPORTING) {
                    if (since != null && since.plusSeconds(status.staleAfterSeconds()).isBefore(now)
                            && watching.plusSeconds(status.staleAfterSeconds()).isBefore(now)) {
                        silent.computeIfAbsent(new SilentProbe(entry.tapUuid(), entry.probeName(), entry.ipVersion()),
                                p -> Lists.newArrayList()).add(entry);
                    }
                    continue;
                }

                raiseAlert(entry, status, tapNames.get(entry.tapUuid()));
            } catch (Exception e) {
                LOG.error("Could not check portal integrity control URL [{}].", entry.uuid(), e);
            }
        }

        for (Map.Entry<SilentProbe, List<PortalIntegrityControlUrlEntry>> probe : silent.entrySet()) {
            try {
                raiseSilentAlert(tenant, probe.getKey(), probe.getValue(), tapNames.get(probe.getKey().tapUuid()));
            } catch (Exception e) {
                LOG.error("Could not raise alert of silent portal integrity probe [{}] of tap [{}].",
                        probe.getKey().probeName(), probe.getKey().tapUuid(), e);
            }
        }
    }

    // One alert per probe and IP version, however many control URLs it requests.
    private void raiseSilentAlert(TenantEntry tenant,
                                  SilentProbe probe,
                                  List<PortalIntegrityControlUrlEntry> entries,
                                  String tapName) {
        DateTime lastReport = entries.stream()
                .map(PortalIntegrityControlUrlEntry::lastReportAt)
                .max(DateTime::compareTo)
                .orElseThrow();

        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("tap_uuid", probe.tapUuid().toString());
        attributes.put("tap_name", text(tapName));
        attributes.put("probe_name", probe.probeName());
        attributes.put("ip_version", String.valueOf(probe.ipVersion()));
        attributes.put("last_report_at", lastReport.toString());
        attributes.put("control_url_count", String.valueOf(entries.size()));

        nzyme.getDetectionAlertService().raiseAlert(
                tenant.organizationUuid(),
                tenant.uuid(),
                null,
                AlertOrigin.tap(probe.tapUuid()),
                DetectionType.PORTAL_INTEGRITY_PROBE_SILENT,
                Subsystem.ETHERNET,
                "Captive portal probe \"" + probe.probeName() + "\" of tap \"" + text(tapName)
                        + "\" stopped reporting over IPv" + probe.ipVersion(),
                attributes,
                Set.of("tap_uuid", "probe_name", "ip_version")
        );
    }

    private void raiseAlert(PortalIntegrityControlUrlEntry entry, PortalIntegrityJudge.Status status, String tapName) {
        String subject = "Captive portal probe \"" + entry.probeName() + "\" of tap \"" + text(tapName)
                + "\" requesting \"" + entry.controlUrl() + "\" over IPv" + entry.ipVersion();

        /*
         * Every attribute is always set, if only to nothing. A raised alert takes new values for the attributes
         * it was created with and ignores the others.
         */
        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("control_url_uuid", entry.uuid().toString());
        attributes.put("tap_uuid", entry.tapUuid().toString());
        attributes.put("tap_name", text(tapName));
        attributes.put("probe_name", entry.probeName());
        attributes.put("control_url", entry.controlUrl());
        attributes.put("ip_version", String.valueOf(entry.ipVersion()));
        attributes.put("report_uuid", text(entry.lastReportUuid()));
        attributes.put("final_url", text(entry.lastFinalUrl()));
        attributes.put("final_address", text(entry.lastFinalAddress()));
        attributes.put("final_certificate", text(entry.lastFinalCertificate()));

        DetectionType type;
        String details;
        Set<String> comparisonKeys = Set.of("control_url_uuid");

        switch (status.result()) {
            case PORTAL_MISSING -> {
                type = DetectionType.PORTAL_INTEGRITY_PORTAL_MISSING;
                details = subject + " was not presented a captive portal";
            }
            case PORTAL_UNEXPECTED -> {
                type = DetectionType.PORTAL_INTEGRITY_PORTAL_UNEXPECTED;
                details = subject + " was presented a captive portal at \"" + entry.lastFinalUrl() + "\"";
            }
            case PORTAL_WRONG -> {
                type = DetectionType.PORTAL_INTEGRITY_PORTAL_WRONG;
                String reasons = status.reasons().stream().map(Enum::name).collect(Collectors.joining(","));
                attributes.put("reasons", reasons);
                comparisonKeys = Set.of("control_url_uuid", "reasons");
                details = subject + " was presented a captive portal at \"" + entry.lastFinalUrl()
                        + "\" that is not the expected portal";
            }
            case REQUEST_FAILED -> {
                type = DetectionType.PORTAL_INTEGRITY_REQUEST_FAILED;
                attributes.put("error", text(entry.lastError()));
                details = subject + " failed: " + entry.lastError();
            }
            default -> {
                return;
            }
        }

        nzyme.getDetectionAlertService().raiseAlert(
                entry.organizationId(),
                entry.tenantId(),
                null,
                AlertOrigin.tap(entry.tapUuid()),
                type,
                Subsystem.ETHERNET,
                details,
                attributes,
                comparisonKeys
        );
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String getName() {
        return "Portal Integrity Monitor";
    }

}
