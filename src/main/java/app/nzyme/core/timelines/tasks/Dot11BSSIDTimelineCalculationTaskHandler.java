package app.nzyme.core.timelines.tasks;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.BSSIDSummary;
import app.nzyme.core.security.authentication.db.TapPermissionEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.timelines.TimelineAddressType;
import app.nzyme.core.timelines.TimelineEventType;
import app.nzyme.core.timelines.Timelines;
import app.nzyme.core.timelines.TimelinesRegistryKeys;
import app.nzyme.core.timelines.resolvers.ResolverResult;
import app.nzyme.core.timelines.resolvers.dot11.bssid.Dot11BSSIDActiveChannelResolver;
import app.nzyme.core.timelines.resolvers.dot11.bssid.Dot11BSSIDFingerprintsResolver;
import app.nzyme.core.timelines.resolvers.dot11.bssid.Dot11BSSIDStrongestTapResolver;
import app.nzyme.core.timelines.resolvers.dot11.bssid.Dot11BSSIDSSIDsAnnouncementResolver;
import app.nzyme.core.util.MetricNames;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.distributed.tasksqueue.ReceivedTask;
import app.nzyme.plugin.distributed.tasksqueue.TaskHandler;
import app.nzyme.plugin.distributed.tasksqueue.TaskProcessingResult;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static app.nzyme.core.timelines.Timelines.EVENT_HORIZON_MINUTES;

public class Dot11BSSIDTimelineCalculationTaskHandler implements TaskHandler {

    private static final Logger LOG = LogManager.getLogger(Dot11BSSIDTimelineCalculationTaskHandler.class);

    private final NzymeNode nzyme;
    private final Timelines timelines;

    private final Timer totalTimer;
    private final Timer individualTimer;

    public Dot11BSSIDTimelineCalculationTaskHandler(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.timelines = new Timelines(nzyme);

        this.totalTimer = nzyme.getMetrics().timer(MetricNames.TIMELINES_BSSIDS_TOTAL_CALCULATION_TIMER);
        this.individualTimer = nzyme.getMetrics().timer(MetricNames.TIMELINES_BSSIDS_INDIVIDUAL_CALCULATION_TIMER);
    }

    @Override
    public TaskProcessingResult handle(ReceivedTask task) {
        try(Timer.Context ignored = totalTimer.time()) {
            LOG.debug("Received task {}", task);

            DateTime now = DateTime.now();

            nzyme.getDatabaseCoreRegistry().setValue(
                    TimelinesRegistryKeys.TIMELINES_DOT11_BSSIDS_LAST_EXECUTION.key(),
                    DateTime.now().toString()
            );

            // For each tenant, find each currently active BSSID and write either a MARK (it's been seen) or an event if there are any.
            for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfAllOrganizations()) {
                List<TapPermissionEntry> tenantTaps = nzyme.getAuthenticationService()
                        .findAllTapsOfTenant(tenant.organizationUuid(), tenant.uuid());
                List<UUID> taps = tenantTaps.stream().map(TapPermissionEntry::uuid).toList();
                Map<UUID, Long> tapIds = tenantTaps.stream()
                        .collect(Collectors.toMap(TapPermissionEntry::uuid, TapPermissionEntry::id, (a, b) -> a));

                /*
                 * Timelines know which taps recorded a BSSID from this moment on, and say so for time ranges before
                 * it. Written under a lock: the registry does not keep two nodes from writing the same key twice.
                 */
                nzyme.getDatabase().useTransaction(handle -> {
                    handle.createQuery("SELECT pg_advisory_xact_lock(hashtext(:key))::text")
                            .bind("key", TimelinesRegistryKeys.DOT11_BSSID_TAPS_RECORDED_SINCE.key() + ":"
                                    + tenant.organizationUuid() + ":" + tenant.uuid())
                            .mapTo(String.class)
                            .list();

                    if (nzyme.getDatabaseCoreRegistry().getValue(TimelinesRegistryKeys.DOT11_BSSID_TAPS_RECORDED_SINCE.key(),
                            tenant.organizationUuid(), tenant.uuid()).isEmpty()) {
                        nzyme.getDatabaseCoreRegistry().setValue(TimelinesRegistryKeys.DOT11_BSSID_TAPS_RECORDED_SINCE.key(),
                                String.valueOf(now.getMillis()), tenant.organizationUuid(), tenant.uuid());
                    }
                });

                for (BSSIDSummary bssid : nzyme.getDot11().findBSSIDs(
                        TimeRange.create(now.minusMinutes(1), now, false),
                        Filters.create(Maps.newHashMap()),
                        Dot11.BssidOrderColumn.BSSID,
                        OrderDirection.ASC,
                        Integer.MAX_VALUE,
                        0,
                        taps)) {
                    try(Timer.Context ignored2 = individualTimer.time()) {
                        int eventsWritten = 0;

                        List<TapBasedSignalStrengthResult> recordedBy = nzyme.getDot11().findBSSIDSignalStrengthPerTap(
                                bssid.bssid(), TimeRange.create(now.minusMinutes(EVENT_HORIZON_MINUTES), now, false), taps
                        );
                        List<Long> recordingTaps = recordedBy.stream()
                                .map(tap -> tapIds.get(tap.tapUuid()))
                                .filter(Objects::nonNull)
                                .toList();

                        // Active channel.
                        Dot11BSSIDActiveChannelResolver channel = new Dot11BSSIDActiveChannelResolver(
                                nzyme, tenant.organizationUuid(), tenant.uuid()
                        );
                        Optional<ResolverResult> channelResult = channel.resolve(bssid.bssid(), taps);

                        if (channelResult.isPresent()) {
                            timelines.writeDot11TimelineEvent(
                                    tenant.organizationUuid(),
                                    tenant.uuid(),
                                    TimelineAddressType.DOT11_BSSID,
                                    bssid.bssid(),
                                    TimelineEventType.DOT11_BSSID_ACTIVE_CHANNEL,
                                    channelResult.get().payload(),
                                    now,
                                    recordingTaps
                            );
                            eventsWritten++;
                        }

                        // SSIDs.
                        Dot11BSSIDSSIDsAnnouncementResolver ssids = new Dot11BSSIDSSIDsAnnouncementResolver(
                                nzyme, tenant.organizationUuid(), tenant.uuid()
                        );
                        Optional<ResolverResult> ssidsResult = ssids.resolve(bssid.bssid(), taps);

                        if (ssidsResult.isPresent()) {
                            timelines.writeDot11TimelineEvent(
                                    tenant.organizationUuid(),
                                    tenant.uuid(),
                                    TimelineAddressType.DOT11_BSSID,
                                    bssid.bssid(),
                                    TimelineEventType.DOT11_BSSID_SSID_DIFF,
                                    ssidsResult.get().payload(),
                                    now,
                                    recordingTaps
                            );
                            eventsWritten++;
                        }

                        // Fingerprints.
                        Dot11BSSIDFingerprintsResolver fingerprints = new Dot11BSSIDFingerprintsResolver(
                                nzyme, tenant.organizationUuid(), tenant.uuid()
                        );
                        Optional<ResolverResult> fingerprintsResult = fingerprints.resolve(bssid.bssid(), taps);

                        if (fingerprintsResult.isPresent()) {
                            timelines.writeDot11TimelineEvent(
                                    tenant.organizationUuid(),
                                    tenant.uuid(),
                                    TimelineAddressType.DOT11_BSSID,
                                    bssid.bssid(),
                                    TimelineEventType.DOT11_BSSID_FINGERPRINT_DIFF,
                                    fingerprintsResult.get().payload(),
                                    now,
                                    recordingTaps
                            );
                            eventsWritten++;
                        }

                        // Strongest Tap.
                        Dot11BSSIDStrongestTapResolver strongestTap = new Dot11BSSIDStrongestTapResolver(
                                nzyme, tenant.organizationUuid(), tenant.uuid()
                        );
                        Optional<ResolverResult> strongestTapResult = strongestTap.resolve(bssid.bssid(), recordedBy);

                        if (strongestTapResult.isPresent()) {
                            timelines.writeDot11TimelineEvent(
                                    tenant.organizationUuid(),
                                    tenant.uuid(),
                                    TimelineAddressType.DOT11_BSSID,
                                    bssid.bssid(),
                                    TimelineEventType.DOT11_BSSID_STRONGEST_TAP,
                                    strongestTapResult.get().payload(),
                                    now,
                                    recordingTaps
                            );
                            eventsWritten++;
                        }

                        // No events, but mark that the BSSID was seen.
                        if (eventsWritten == 0) {
                            timelines.writeDot11TimelineEvent(
                                    tenant.organizationUuid(),
                                    tenant.uuid(),
                                    TimelineAddressType.DOT11_BSSID,
                                    bssid.bssid(),
                                    TimelineEventType.MARK,
                                    Collections.emptyMap(),
                                    now,
                                    recordingTaps
                            );
                        }

                    } catch (Exception e) {
                        LOG.error("Error while calculating timeline for 802.11 BSSID [{}]. Skipping.", "FOO", e);
                    }
                }
            }

            return TaskProcessingResult.SUCCESS;
        }
    }

    @Override
    public String getName() {
        return "802.11 BSSID Timeline Calculation";
    }


}
