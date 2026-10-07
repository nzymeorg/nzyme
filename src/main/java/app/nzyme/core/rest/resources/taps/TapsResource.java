package app.nzyme.core.rest.resources.taps;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.responses.taps.metrics.*;
import app.nzyme.core.taps.db.EngagementLogEntry;
import app.nzyme.core.taps.db.metrics.*;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import app.nzyme.plugin.rest.security.RESTSecured;
import app.nzyme.core.rest.responses.taps.*;
import app.nzyme.core.taps.Bus;
import app.nzyme.core.taps.Capture;
import app.nzyme.core.taps.Channel;
import app.nzyme.core.taps.Tap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/taps")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Taps", description = "Taps are the sensors that capture network data and report it to Nzyme.")
public class TapsResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(PermissionLevel.ANY)
    @Path("/highlevel")
    @Operation(operationId = "findTapsHighLevel", summary = "List taps with high-level status",
            description = "Returns name, location and active status of all taps of a tenant that the calling user can access. "
                    + "Available to any user. The full tap details require organization administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Taps found.",
            content = @Content(schema = @Schema(implementation = TapHighLevelInformationListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAllWithHighLevelInformation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Get all UUIDs of taps the user can access.
        List<UUID> uuids = parseAndValidateTapIdsDirect(
                authenticatedUser,
                nzyme,
                nzyme.getTapManager().allTapUUIDsAccessibleByScope(organizationId, tenantId)
        );

        List<TapHighLevelInformationDetailsResponse> tapsResponse = Lists.newArrayList();
        for (Tap tap : nzyme.getTapManager().findAllTapsByUUIDs(uuids)) {
            Optional<TenantLocationEntry> location = nzyme
                    .getAuthenticationService().findTenantLocation(tap.locationId(), organizationId, tenantId);

            String locationName;
            String floorName;
            if (location.isPresent()) {
                locationName = location.get().name();
                Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                        .findFloorOfTenantLocation(location.get().uuid(), tap.floorId());
                if (floor.isPresent()) {
                    floorName = floor.get().name();
                } else {
                    floorName = null;
                }
            } else {
                locationName = null;
                floorName = null;
            }

            tapsResponse.add(TapHighLevelInformationDetailsResponse.create(
                    tap.uuid(), tap.name(), locationName, floorName, Tools.isTapActive(tap.lastReport())
            ));
        }

        return Response.ok(TapHighLevelInformationListResponse.create(tapsResponse)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ANY)
    @Path("/show/{uuid}/highlevel")
    @Operation(operationId = "findTapHighLevel", summary = "Get high-level status of a tap")
    @ApiResponse(responseCode = "200", description = "Tap found.",
            content = @Content(schema = @Schema(implementation = TapHighLevelInformationDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response findOneWithHighLevelInformation(@Parameter(hidden = true) @Context SecurityContext sc,
                                                    @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid,
                                                    @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                                                    @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)
                || !tapIdAccessible(authenticatedUser, nzyme, uuid)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TenantLocationEntry> location = nzyme
                .getAuthenticationService().findTenantLocation(tap.get().locationId(), organizationId, tenantId);

        String locationName;
        String floorName;
        if (location.isPresent()) {
            locationName = location.get().name();
            Optional<TenantLocationFloorEntry> floor = nzyme.getAuthenticationService()
                    .findFloorOfTenantLocation(location.get().uuid(), tap.get().floorId());
            floorName = floor.map(TenantLocationFloorEntry::name).orElse(null);
        } else {
            locationName = null;
            floorName = null;
        }

        return Response.ok(TapHighLevelInformationDetailsResponse.create(
                tap.get().uuid(),
                tap.get().name(),
                locationName,
                floorName,
                Tools.isTapActive(tap.get().lastReport())
        )).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Operation(operationId = "findTaps", summary = "List taps of a tenant",
            description = "Returns full details of all taps of a tenant, including configuration and capture state.")
    @ApiResponse(responseCode = "200", description = "Taps found.",
            content = @Content(schema = @Schema(implementation = TapListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") @NotNull UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") @NotNull UUID tenantId) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Get all UUIDs of taps the user can access.
        List<UUID> uuids = parseAndValidateTapIdsDirect(
                authenticatedUser,
                nzyme,
                nzyme.getTapManager().allTapUUIDsAccessibleByScope(organizationId, tenantId)
        );

        List<TapDetailsResponse> tapsResponse = Lists.newArrayList();
        for (Tap tap : nzyme.getTapManager().findAllTapsByUUIDs(uuids)) {
            Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                    .findTenantLocation(tap.locationId(), organizationId, tenantId);

            Optional<TenantLocationFloorEntry> floor;
            if (location.isPresent()) {
                floor = nzyme.getAuthenticationService()
                        .findFloorOfTenantLocation(location.get().uuid(), tap.floorId());
            } else {
                floor = Optional.empty();
            }

            tapsResponse.add(buildTapResponse(tap, location, floor));
        }

        return Response.ok(TapListResponse.create(tapsResponse.size(), tapsResponse)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{uuid}")
    @Operation(operationId = "findTapDetails", summary = "Get tap details")
    @ApiResponse(responseCode = "200", description = "Tap found.",
            content = @Content(schema = @Schema(implementation = TapDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Tap exists but belongs to an organization or tenant the calling user cannot administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response findTap(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(uuid)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            Optional<TenantLocationEntry> location = nzyme.getAuthenticationService()
                    .findTenantLocation(
                            tap.get().locationId(),
                            authenticatedUser.getOrganizationId(),
                            authenticatedUser.getTenantId()
                    );

            Optional<TenantLocationFloorEntry> floor;
            if (location.isPresent()) {
                floor = nzyme.getAuthenticationService()
                        .findFloorOfTenantLocation(location.get().uuid(), tap.get().floorId());
            } else {
                floor = Optional.empty();
            }

            return Response.ok(buildTapResponse(tap.get(), location, floor)).build();
        }
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{uuid}/metrics")
    @Operation(operationId = "findTapMetrics", summary = "Get current metrics of a tap",
            description = "Returns the most recent value of every gauge and timer metric the tap reported.")
    @ApiResponse(responseCode = "200", description = "Metrics found.",
            content = @Content(schema = @Schema(implementation = TapMetricsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Tap exists but belongs to an organization or tenant the calling user cannot administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response tapMetrics(@Parameter(hidden = true) @Context SecurityContext sc, @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(uuid)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<String, TapMetricsGaugeResponse> parsedGauges = Maps.newHashMap();
        for (TapMetricsGauge gauge : nzyme.getTapManager().findGaugesOfTap(uuid)) {
            if (gauge.metricName().startsWith("captures") || gauge.metricName().startsWith("channels")) {
                continue;
            }

            parsedGauges.put(
                    gauge.metricName(),
                    TapMetricsGaugeResponse.create(
                            gauge.metricName(),
                            gauge.metricValue(),
                            gauge.createdAt()
                    )
            );
        }

        Map<String, TapMetricsTimerResponse> parsedTimers = Maps.newHashMap();
        for (TapMetricsTimer timer : nzyme.getTapManager().findTimersOfTap(uuid)) {
            parsedTimers.put(
                    timer.metricName(),
                    TapMetricsTimerResponse.create(
                            timer.metricName(),
                            timer.mean(),
                            timer.p99(),
                            timer.createdAt()
                    )
            );
        }

        return Response.ok(TapMetricsResponse.create(parsedGauges, parsedTimers)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{uuid}/metrics/gauges/{metricName}/histogram")
    @Operation(operationId = "findTapGaugeHistogram", summary = "Get 24 hour histogram of a tap gauge metric",
            description = "Returns an empty object if the tap has not reported this metric.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TapMetricsHistogramResponse.class)))
    @ApiResponse(responseCode = "401", description = "Tap exists but belongs to an organization or tenant the calling user cannot administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response tapMetricsGauge(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid,
                                    @Parameter(description = "Metric name as reported by the tap.") @PathParam("metricName") String metricName) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(uuid)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Map<DateTime, TapMetricsTimerHistogramAggregation>> histo = nzyme.getTapManager().findMetricsGaugeHistogram(
                uuid, metricName, 24, BucketSize.MINUTE
        );

        if (histo.isEmpty()) {
            return Response.ok(Maps.newHashMap()).build();
        }

        Map<DateTime, TapMetricsHistogramValueResponse> result = Maps.newTreeMap();
        for (TapMetricsTimerHistogramAggregation value : histo.get().values()) {
            result.put(value.bucket(), TapMetricsHistogramValueResponse.create(
                    value.bucket(), value.average(), value.maximum(), value.minimum()
            ));
        }

        return Response.ok(TapMetricsHistogramResponse.create(result)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{uuid}/metrics/timers/{metricName}/histogram")
    @Operation(operationId = "findTapTimerHistogram", summary = "Get 24 hour histogram of a tap timer metric",
            description = "Returns an empty object if the tap has not reported this metric.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TapMetricsHistogramResponse.class)))
    @ApiResponse(responseCode = "401", description = "Tap exists but belongs to an organization or tenant the calling user cannot administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response tapMetricsTimer(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid,
                                    @Parameter(description = "Metric name as reported by the tap.") @PathParam("metricName") String metricName) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(uuid)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<Map<DateTime, TapMetricsTimerHistogramAggregation>> histo = nzyme.getTapManager().findMetricsTimerHistogram(
                uuid, metricName, 24, BucketSize.MINUTE
        );

        if (histo.isEmpty()) {
            return Response.ok(Maps.newHashMap()).build();
        }

        Map<DateTime, TapMetricsHistogramValueResponse> result = Maps.newTreeMap();
        for (TapMetricsTimerHistogramAggregation value : histo.get().values()) {
            result.put(value.bucket(), TapMetricsHistogramValueResponse.create(
                    value.bucket(), value.average(), value.maximum(), value.minimum()
            ));
        }

        return Response.ok(TapMetricsHistogramResponse.create(result)).build();
    }

    @GET
    @RESTSecured(PermissionLevel.ORGADMINISTRATOR)
    @Path("/show/{uuid}/engagement/logs")
    @Operation(operationId = "findTapEngagementLogs", summary = "List engagement logs of a tap",
            description = "Engagement logs record when a tap connected, disconnected or changed state.")
    @ApiResponse(responseCode = "200", description = "Logs found.",
            content = @Content(schema = @Schema(implementation = TapEngagementLogsListResponse.class)))
    @ApiResponse(responseCode = "401", description = "Tap exists but belongs to an organization or tenant the calling user cannot administer.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Tap not found or not accessible by the calling user.", content = @Content)
    public Response tapMetricsTimer(@Parameter(hidden = true) @Context SecurityContext sc,
                                    @Parameter(description = "Tap UUID.") @PathParam("uuid") UUID uuid,
                                    @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                    @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        if (!nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser).contains(uuid)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(uuid);

        if (tap.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = nzyme.getTapManager().countEngagementLogOfTap(tap.get().uuid());

        List<TapEngagementLogDetailsResponse> logs = Lists.newArrayList();
        for (EngagementLogEntry log : nzyme.getTapManager().findEngagementLogOfTap(tap.get().uuid(), limit, offset)) {
            logs.add(TapEngagementLogDetailsResponse.create(log.message(), log.timestamp()));
        }

        return Response.ok(TapEngagementLogsListResponse.create(total, logs)).build();
    }

    private TapDetailsResponse buildTapResponse(Tap tap,
                                                Optional<TenantLocationEntry> location,
                                                Optional<TenantLocationFloorEntry> floor) {
        List<BusDetailsResponse> busesResponse = Lists.newArrayList();

        Optional<List<Bus>> buses = nzyme.getTapManager().findBusesOfTap(tap.uuid());
        if (buses.isPresent()) {
            for (Bus bus : buses.get()) {
                List<ChannelDetailsResponse> channelsResponse = Lists.newArrayList();

                Optional<List<Channel>> channels = nzyme.getTapManager().findChannelsOfBus(bus.id());
                if (channels.isPresent()) {
                    for (Channel channel : channels.get()) {
                        channelsResponse.add(ChannelDetailsResponse.create(
                                channel.name(),
                                channel.capacity(),
                                channel.watermark(),
                                TotalWithAverageResponse.create(
                                        channel.errors().total(),
                                        channel.errors().average()
                                ),
                                TotalWithAverageResponse.create(
                                        channel.throughputBytes().total(),
                                        channel.throughputBytes().average()
                                ),
                                TotalWithAverageResponse.create(
                                        channel.throughputMessages().total(),
                                        channel.throughputMessages().average()
                                )
                        ));
                    }
                }

                busesResponse.add(BusDetailsResponse.create(
                        bus.id(),
                        bus.name(),
                        channelsResponse
                ));
            }
        }

        List<CaptureDetailsResponse> capturesResponse = Lists.newArrayList();
        for (Capture capture : nzyme.getTapManager().findActiveCapturesOfTap(tap.uuid())) {
            capturesResponse.add(
                    CaptureDetailsResponse.create(
                            capture.interfaceName(),
                            capture.captureType(),
                            capture.isRunning(),
                            capture.received(),
                            capture.droppedBuffer(),
                            capture.droppedInterface(),
                            capture.cycleTime(),
                            capture.updatedAt(),
                            capture.createdAt()
                    )
            );
        }

        List<TapFrequencyAndChannelWidthsResponse> dot11Frequencies = Lists.newArrayList();
        for (Dot11FrequencyAndChannelWidthEntry fcw : nzyme.getTapManager().findDot11FrequenciesOfTap(tap.uuid())) {
            dot11Frequencies.add(TapFrequencyAndChannelWidthsResponse.create(fcw.frequency(), fcw.channelWidths()));
        }

        return TapDetailsResponse.create(
                tap.uuid(),
                tap.name(),
                tap.version(),
                tap.clock(),
                TotalWithAverageResponse.create(tap.processedBytes().total(), tap.processedBytes().average()),
                tap.memoryTotal(),
                tap.memoryFree(),
                tap.memoryUsed(),
                tap.cpuLoad(),
                Tools.isTapActive(tap.lastReport()),
                tap.clockDriftMs(),
                tap.rpi(),
                tap.createdAt(),
                tap.updatedAt(),
                tap.lastReport(),
                tap.description(),
                busesResponse,
                capturesResponse,
                tap.remoteAddress(),
                dot11Frequencies,
                tap.organizationId(),
                tap.tenantId(),
                tap.locationId(),
                location.map(TenantLocationEntry::name).orElse(null),
                tap.floorId(),
                floor.map(Tools::buildFloorName).orElse(null),
                tap.latitude(),
                tap.longitude(),
                tap.configuration()
        );
    }

}
