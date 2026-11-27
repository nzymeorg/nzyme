package app.nzyme.core.rest.resources.timelines;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.DataCategory;
import app.nzyme.core.database.DatabaseTools;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.RestTools;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import app.nzyme.core.rest.responses.timelines.TimelineActivityHistogramBucketResponse;
import app.nzyme.core.rest.responses.timelines.TimelineActivityHistogramResponse;
import app.nzyme.core.rest.responses.timelines.TimelineEventDetailsResponse;
import app.nzyme.core.rest.responses.timelines.TimelineResponse;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.timelines.TimelineAddressType;
import app.nzyme.core.timelines.Timelines;
import app.nzyme.core.timelines.TimelinesRegistryKeys;
import app.nzyme.core.timelines.db.TimelineActivityHistogram;
import app.nzyme.core.timelines.db.TimelineActivityHistogramBucket;
import app.nzyme.core.timelines.db.TimelineEventEntry;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/timelines")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Timelines", description = "A timeline shows how the parameters of a WiFi access point or network "
        + "changed over time, addressed by BSSID or by SSID. A background task computes the timeline events from the "
        + "changes it sees between periods, which makes timelines a tool for investigation rather than for alerting.",
        externalDocs = @ExternalDocumentation(description = "WiFi timelines in the Nzyme documentation",
                url = "https://go.nzyme.org/wifi-timelines"))
public class TimelinesResource extends UserAuthenticatedResource {

    @Inject
    private NzymeNode nzyme;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JodaModule())
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final static int GAP_THRESHOLD = 10;

    @GET
    @Path("/show/type/{addressType}/address/{address}")
    @Operation(operationId = "findTimeline", summary = "Get the timeline of an address",
            description = "Returns the events recorded for this address in the requested time range, newest first, "
                    + "together with an activity histogram and the event retention time of the tenant. All taps of "
                    + "the tenant are considered, no matter which taps the user selected elsewhere. For BSSID "
                    + "addresses the response also lists advertised SSIDs, fingerprints and the taps that recorded "
                    + "the address in the time range. They are taken from recorded 802.11 data and, for the part of "
                    + "the time range that is older than its retention time, from the events of the timeline.")
    @ApiResponse(responseCode = "200", description = "Timeline found.",
            content = @Content(schema = @Schema(implementation = TimelineResponse.class)))
    @ApiResponse(responseCode = "403", description = "Organization or tenant not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Type of the address: DOT11_BSSID or DOT11_SSID.") @PathParam("addressType") TimelineAddressType addressType,
                            @Parameter(description = "The address itself: a BSSID MAC address or an SSID name.") @PathParam("address") String address,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") String timeRangeParameter,
                            @Parameter(description = "JSON encoded list of event type names to exclude from the timeline.") @QueryParam("excluded_event_types") String excludedEventTypesP,
                            @Parameter(description = "Time zone the activity histogram buckets are aligned to. Falls back to UTC.") @QueryParam("time_zone") String timeZone,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        List<String> excludedEventTypes = this.objectMapper.readValue(excludedEventTypesP, new TypeReference<>() {});

        Timelines timelines = new Timelines(nzyme);
        long total = timelines.countAllEventsOfAddress(
                organizationId, tenantId, addressType, address, timeRange, excludedEventTypes, GAP_THRESHOLD
        );

        List<TimelineEventDetailsResponse> events = Lists.newArrayList();
        for (TimelineEventEntry e : timelines.findAllEventsOfAddress(
                organizationId, tenantId, addressType, address, timeRange, excludedEventTypes, GAP_THRESHOLD, limit, offset)) {

            events.add(TimelineEventDetailsResponse.create(
                    e.uuid(),
                    e.address(),
                    e.addressType(),
                    e.eventType(),
                    objectMapper.readValue(e.eventDetails(), new TypeReference<>(){}),
                    e.timestamp()
            ));
        }

        List<UUID> tenantTapIds = nzyme.getTapManager()
                .findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).toList();

        List<String> ssids;
        boolean hasHiddenSsid;
        List<String> fingerprints;
        List<TapHighLevelInformationDetailsResponse> recordingTaps;
        DateTime tapsCompleteSince = null;
        Integer tapsRetentionDays = null;
        List<String> limitedLists = Collections.emptyList();
        if (addressType == TimelineAddressType.DOT11_BSSID) {
            /*
             * Recorded reports are deleted long before events are. What they hold of the time range is complete, and
             * what the events hold covers the part of the time range that the reports no longer do.
             */
            Timelines.RecordedBssidState recorded = timelines
                    .findRecordedBssidState(organizationId, tenantId, address, timeRange);

            limitedLists = Lists.newArrayList(recorded.limited());

            Set<String> allSsids = Sets.newTreeSet(recorded.ssids());
            allSsids.addAll(nzyme.getDot11().findSSIDsAdvertisedByBSSID(address, tenantTapIds, timeRange));
            ssids = Lists.newArrayList(allSsids);

            // An access point that was seen sent a name or none. One that was never known by a name sent none.
            hasHiddenSsid = nzyme.getDot11().bssidAdvertisedWithoutSsid(address, timeRange, tenantTapIds)
                    || (recorded.active() && !recorded.knowsSsids() && ssids.isEmpty());

            Set<String> allFingerprints = Sets.newTreeSet(recorded.fingerprints());
            nzyme.getDot11().findFingerprintsOfBSSID(address, timeRange, tenantTapIds).stream()
                    .filter(Objects::nonNull)
                    .forEach(allFingerprints::add);
            fingerprints = Lists.newArrayList(allFingerprints);

            Set<UUID> tapIds = Sets.newLinkedHashSet();
            nzyme.getDot11().findBSSIDSignalStrengthPerTap(address, timeRange, tenantTapIds)
                    .forEach(tap -> tapIds.add(tap.tapUuid()));
            int tapsOfReports = tapIds.size();
            recorded.taps().stream()
                    .filter(tenantTapIds::contains)
                    .forEach(tapIds::add);

            recordingTaps = Lists.newArrayList();
            for (UUID tapId : tapIds) {
                Optional<Tap> tap = nzyme.getTapManager().findTap(tapId);

                if (tap.isEmpty()) {
                    continue;
                }

                String locationName;
                String floorName;
                if (tap.get().locationId() != null) {
                    locationName = nzyme.getAuthenticationService()
                            .findTenantLocation(tap.get().locationId(), organizationId, tenantId)
                            .map(TenantLocationEntry::name)
                            .orElse(null);

                    if (locationName != null && tap.get().floorId() != null) {
                        floorName = nzyme.getAuthenticationService()
                                .findFloorOfTenantLocation(tap.get().locationId(), tap.get().floorId())
                                .map(Tools::buildFloorName)
                                .orElse(null);
                    } else {
                        floorName = null;
                    }
                } else {
                    locationName = null;
                    floorName = null;
                }

                recordingTaps.add(TapHighLevelInformationDetailsResponse.create(
                        tap.get().uuid(),
                        tap.get().name(),
                        locationName,
                        floorName,
                        Tools.isTapActive(tap.get().lastReport())
                ));
            }

            // Taps of reports come by signal strength. Events hold no signal strength, so their taps follow by name.
            if (recordingTaps.size() > tapsOfReports) {
                recordingTaps.subList(tapsOfReports, recordingTaps.size()).sort(Comparator.comparing(
                        TapHighLevelInformationDetailsResponse::name, String.CASE_INSENSITIVE_ORDER
                ));
            }

            // Every tap is known as far back as reports are kept, or events with their taps, whichever reaches further.
            int dataRetentionDays = DatabaseTools
                    .getDataCategoryRetentionTimeDays(nzyme, DataCategory.DOT11, organizationId, tenantId);
            DateTime reportsSince = DateTime.now().minusDays(dataRetentionDays);
            Optional<DateTime> recordedSince = tapsRecordedSince(organizationId, tenantId)
                    .filter(since -> since.isBefore(reportsSince));

            if (timeRange.from().isBefore(recordedSince.orElse(reportsSince))) {
                tapsCompleteSince = recordedSince.orElse(reportsSince);
                tapsRetentionDays = recordedSince.isPresent() ? null : dataRetentionDays;
            }
        } else {
            ssids = Collections.emptyList();
            hasHiddenSsid = false;
            fingerprints = Collections.emptyList();
            recordingTaps = Collections.emptyList();
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        int retentionTimeDays = Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.key(), organizationId, tenantId)
                .orElse(TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.defaultValue().get()));

        String tz;
        try {
            tz = ZoneId.of(timeZone).getId();
        } catch (Exception e) {
            tz = "UTC";
        }

        TimelineActivityHistogram ah = timelines.getEventTypeActivityHistogram(
                organizationId, tenantId, addressType, address, timeRange, excludedEventTypes, GAP_THRESHOLD, tz
        );

        List<TimelineActivityHistogramBucketResponse> buckets = Lists.newArrayList();
        for (TimelineActivityHistogramBucket bucket : ah.buckets()) {
            buckets.add(TimelineActivityHistogramBucketResponse.create(
                    bucket.bucket(), bucket.total(), bucket.countsByEventType())
            );
        }

        TimelineActivityHistogramResponse activityHistogramResponse = TimelineActivityHistogramResponse.create(
                ah.from(), ah.to(), ah.bucketType().toString(), ah.totalsByEventType(), buckets
        );

        return Response.ok(TimelineResponse.create(
                retentionTimeDays,
                total,
                events,
                ssids,
                hasHiddenSsid,
                fingerprints,
                recordingTaps,
                tapsCompleteSince,
                tapsRetentionDays,
                limitedLists,
                Timelines.MAX_RECORDED_VALUES,
                activityHistogramResponse
        )).build();
    }

    private Optional<DateTime> tapsRecordedSince(UUID organizationId, UUID tenantId) {
        try {
            return nzyme.getDatabaseCoreRegistry()
                    .getValue(TimelinesRegistryKeys.DOT11_BSSID_TAPS_RECORDED_SINCE.key(), organizationId, tenantId)
                    .map(value -> new DateTime(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
