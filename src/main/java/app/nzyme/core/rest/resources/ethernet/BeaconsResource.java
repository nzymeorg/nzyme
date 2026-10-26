package app.nzyme.core.rest.resources.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.db.AssetEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.ethernet.beacons.*;
import app.nzyme.core.ethernet.beacons.db.BeaconCheckinEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconExclusionEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconNoteEntry;
import app.nzyme.core.ethernet.beacons.db.BeaconSourceEntry;
import app.nzyme.core.registry.RegistryChangeValidator;
import app.nzyme.core.ethernet.GeoData;
import app.nzyme.core.ethernet.L4Type;
import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;
import app.nzyme.core.integrations.geoip.GeoIpLookupResult;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import app.nzyme.core.rest.requests.GenericConfigurationUpdateRequest;
import app.nzyme.core.rest.requests.CreateBeaconNoteRequest;
import app.nzyme.core.rest.requests.UpdateBeaconRequest;
import app.nzyme.core.rest.responses.ethernet.beacons.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.RegistryKey;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/ethernet/beacons")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Beacons", description = "A beacon is a destination that one or more devices in the monitored "
        + "Ethernet traffic call out to in regular intervals. This is how software checks for updates and reports "
        + "telemetry, but also how malware maintains a command and control channel. Nzyme detects beacons passively "
        + "from TLS, HTTP, SSH, SOCKS, DNS, TCP and UDP traffic, groups them by destination and gives each one a "
        + "random name until an operator names and classifies it. Beacons are never retention cleaned, only their "
        + "check-in history is.")
public class BeaconsResource extends TapDataHandlingResource {

    private static final Logger LOG = LogManager.getLogger(BeaconsResource.class);

    private static final String MANAGE_PERMISSION = "ethernet_monitoring_manage";

    private final ObjectMapper om = new ObjectMapper();

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findBeacons", summary = "List beacons of a tenant",
            description = "Returns the beacons of the tenant that were active at any point inside the time range, "
                    + "most recently seen first by default. Each beacon carries its destination, name, "
                    + "classification, number of calling devices, the median check-in interval in seconds and its "
                    + "jitter. The response also includes the number of beacons per classification inside the time "
                    + "range, independent of the filters. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Beacons found.",
            content = @Content(schema = @Schema(implementation = BeaconsListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A beacon matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column. Defaults to the time the beacon was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Beacons.OrderColumn orderColumn = Beacons.OrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Beacons.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        long total = beacons.countBeacons(filters, timeRange, organizationId, tenantId);
        Map<BeaconExclusionEntry, BeaconExclusionMatcher> exclusions = beacons.exclusions()
                .compileEnabled(organizationId, tenantId);
        List<BeaconDetailsResponse> result = Lists.newArrayList();
        for (BeaconEntry beacon : beacons.findAllBeacons(
                filters, timeRange, orderColumn, orderDirection, limit, offset, organizationId, tenantId)) {
            result.add(toResponse(beacon, organizationId, tenantId, exclusions));
        }

        return Response.ok(BeaconsListResponse.create(
                total, beacons.countBeaconsByClassification(timeRange, organizationId, tenantId), result
        )).build();
    }

    @GET
    @Path("/histogram/active")
    @Operation(operationId = "findActiveBeaconsHistogram", summary = "Get the number of active beacons over time",
            description = "Returns the number of distinct beacons of the tenant that recorded at least one check-in "
                    + "per time bucket. Respects the same filters as the beacons list. The response is an object that maps each bucket timestamp to a count. The "
                    + "bucket size is chosen automatically from the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response activeBeaconsHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Same fields as the beacons list.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getEthernet().beacons().activeBeaconsHistogram(
                filters, organizationId, tenantId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{beacon_uuid}")
    @Operation(operationId = "findBeacon", summary = "Get a beacon",
            description = "Returns one beacon with all of its details, including the enrichment data collected "
                    + "from the underlying protocol, like TLS server name, JA4 fingerprint and certificate details.")
    @ApiResponse(responseCode = "200", description = "Beacon found.",
            content = @Content(schema = @Schema(implementation = BeaconDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<BeaconEntry> beacon = nzyme.getEthernet().beacons().findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<BeaconExclusionEntry, BeaconExclusionMatcher> exclusions = nzyme.getEthernet().beacons().exclusions()
                .compileEnabled(organizationId, tenantId);

        return Response.ok(toResponse(beacon.get(), organizationId, tenantId, exclusions)).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/sources")
    @Operation(operationId = "findBeaconSources", summary = "List devices calling a beacon",
            description = "Returns the devices (sources) that called out to the beacon destination at any point "
                    + "inside the time range. Each source carries its own check-in count, median interval, jitter "
                    + "and regularity, which is the share of its intervals that fell within the jitter tolerance. "
                    + "The per-source figures cover the whole life of the source, not only the time range. Sources "
                    + "are linked to assets by MAC address where possible. Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Sources found.",
            content = @Content(schema = @Schema(implementation = BeaconSourcesListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findSources(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. A source matches when it was active at any point inside the range.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                @Parameter(description = "Sorting column. Defaults to the time the source was last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Beacons.SourceOrderColumn orderColumn = Beacons.SourceOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Beacons.SourceOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long total = beacons.countSourcesOfBeacon(beacon.get().id(), timeRange);
        List<BeaconSourceResponse> sources = Lists.newArrayList();
        for (BeaconSourceEntry source : beacons.findSourcesOfBeacon(
                beacon.get().id(), timeRange, orderColumn, orderDirection, limit, offset)) {
            sources.add(toResponse(source, organizationId, tenantId));
        }

        return Response.ok(BeaconSourcesListResponse.create(total, sources)).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/checkins/histogram")
    @Operation(operationId = "findBeaconCheckinHistogram", summary = "Get the check-in histogram of a beacon",
            description = "Returns the number of recorded check-ins per time bucket. The response is an object that "
                    + "maps each bucket timestamp to a count. The bucket size is chosen automatically from the time "
                    + "range. Optionally limited to one source of the beacon.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, source, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response checkinHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                     @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                                     @Parameter(description = "Optional source UUID to limit the histogram to one calling device.") @QueryParam("source_uuid") @Nullable UUID sourceUuid,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Long sourceId = null;
        if (sourceUuid != null) {
            Optional<BeaconSourceEntry> source = beacons.findSourceOfBeacon(sourceUuid, beacon.get().id());
            if (source.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            sourceId = source.get().id();
        }

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : beacons.checkinHistogram(
                beacon.get().id(), sourceId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/checkins/histogram/bytes")
    @Operation(operationId = "findBeaconCheckinBytesHistogram", summary = "Get the check-in size histogram of a beacon",
            description = "Returns the average size of a check-in in bytes per time bucket. The size of a check-in "
                    + "is the total number of bytes transferred by its underlying connections. Check-ins without "
                    + "size information, like DNS queries, are not included. The response is an object that maps "
                    + "each bucket timestamp to the average. Optionally limited to one source of the beacon.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, source, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response checkinBytesHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                          @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                                          @Parameter(description = "Optional source UUID to limit the histogram to one calling device.") @QueryParam("source_uuid") @Nullable UUID sourceUuid,
                                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Long sourceId = null;
        if (sourceUuid != null) {
            Optional<BeaconSourceEntry> source = beacons.findSourceOfBeacon(sourceUuid, beacon.get().id());
            if (source.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            sourceId = source.get().id();
        }

        Map<DateTime, Integer> histogram = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : beacons.checkinBytesHistogram(
                beacon.get().id(), sourceId, timeRange, bucketing)) {
            histogram.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/destination/connections")
    @Operation(operationId = "findBeaconDestinationConnections", summary = "Summarize all connections to the beacon destination",
            description = "Returns all TCP and UDP connections to the destination address of the beacon that the "
                    + "taps of the tenant recorded in the time range, summarized by L4 type and destination port. "
                    + "This includes the beacon connections themselves and shows what else devices talk to the "
                    + "destination about. Empty for beacons without a known destination address.")
    @ApiResponse(responseCode = "200", description = "Summary found.",
            content = @Content(schema = @Schema(implementation = BeaconDestinationConnectionsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response destinationConnections(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                           @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                           @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String destinationAddress = beacon.get().destinationAddress();
        if (destinationAddress == null || !BeaconDetector.isValidAddress(destinationAddress)) {
            return Response.ok(BeaconDestinationConnectionsResponse.create(null, Collections.emptyList())).build();
        }

        List<UUID> taps = nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)
                .stream().map(Tap::uuid).toList();

        String beaconL4Type = BeaconType.UDP.name().equals(beacon.get().type())
                || BeaconType.DNS.name().equals(beacon.get().type()) ? "UDP" : "TCP";

        List<BeaconDestinationConnectionResponse> connections = Lists.newArrayList();
        for (Beacons.DestinationConnectionSummary c : beacons.findDestinationConnections(destinationAddress, taps, timeRange)) {
            boolean isBeaconPort = beacon.get().destinationPort() != null
                    && beacon.get().destinationPort() == c.destinationPort()
                    && beaconL4Type.equals(c.l4Type());

            connections.add(BeaconDestinationConnectionResponse.create(
                    c.l4Type(),
                    c.destinationPort(),
                    isBeaconPort,
                    c.sessionCount(),
                    c.sourceCount(),
                    c.bytesRx(),
                    c.bytesTx(),
                    c.firstSeen(),
                    c.lastSeen()
            ));
        }

        return Response.ok(BeaconDestinationConnectionsResponse.create(destinationAddress, connections)).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/checkins/points")
    @Operation(operationId = "findBeaconCheckinPoints", summary = "Get individual check-ins of a beacon for plotting",
            description = "Returns every check-in in the time range as a point with its timestamp, size in bytes "
                    + "and the number of seconds since the previous check-in of the same device. Intended for "
                    + "scatter plots. Returns the most recent points up to a fixed limit and flags truncation. "
                    + "Optionally limited to one source of the beacon and filtered like the check-ins list.")
    @ApiResponse(responseCode = "200", description = "Points found.",
            content = @Content(schema = @Schema(implementation = BeaconCheckinPointsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, source, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response checkinPoints(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                                  @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                                  @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                                  @Parameter(description = "Optional source UUID to limit the points to one calling device.") @QueryParam("source_uuid") @Nullable UUID sourceUuid,
                                  @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Same fields as the check-ins list.") @QueryParam("filters") @Nullable String filtersParameter,
                                  @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        final int limit = 10000;

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Long sourceId = null;
        if (sourceUuid != null) {
            Optional<BeaconSourceEntry> source = beacons.findSourceOfBeacon(sourceUuid, beacon.get().id());
            if (source.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            sourceId = source.get().id();
        }

        Map<Long, BeaconSourceEntry> sources = Maps.newHashMap();
        for (BeaconSourceEntry source : beacons.findAllSourcesOfBeacon(beacon.get().id())) {
            sources.put(source.id(), source);
        }

        List<Beacons.CheckinPoint> points = beacons.findCheckinPoints(beacon.get().id(), sourceId, filters, timeRange, limit);

        List<BeaconCheckinPointResponse> result = Lists.newArrayList();
        for (Beacons.CheckinPoint point : points) {
            BeaconSourceEntry source = sources.get(point.sourceId());
            if (source == null) {
                continue;
            }

            result.add(BeaconCheckinPointResponse.create(
                    point.timestamp(), source.uuid(), source.sourceAddress(), point.bytesTotal(), point.intervalSeconds()
            ));
        }

        return Response.ok(BeaconCheckinPointsResponse.create(limit, points.size() >= limit, result)).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/checkins")
    @Operation(operationId = "findBeaconCheckins", summary = "List check-ins of a beacon",
            description = "Returns the recorded check-ins of a beacon in the time range, most recent first. A "
                    + "check-in is one connection or query of a source to the beacon destination, with parallel "
                    + "connections collapsed into one. Optionally limited to one source and filtered by client "
                    + "address or MAC, destination address or MAC, size and number of parallel connections. "
                    + "Results are paginated.")
    @ApiResponse(responseCode = "200", description = "Check-ins found.",
            content = @Content(schema = @Schema(implementation = BeaconCheckinsListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, source, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response checkins(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                             @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                             @Parameter(description = "Optional source UUID to limit the list to one calling device.") @QueryParam("source_uuid") @Nullable UUID sourceUuid,
                             @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") @Nullable String filtersParameter,
                             @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                             @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                             @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Long sourceId = null;
        if (sourceUuid != null) {
            Optional<BeaconSourceEntry> source = beacons.findSourceOfBeacon(sourceUuid, beacon.get().id());
            if (source.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            sourceId = source.get().id();
        }

        // Sources and taps of this beacon, for enriching the check-ins.
        Map<Long, BeaconSourceEntry> sources = Maps.newHashMap();
        for (BeaconSourceEntry source : beacons.findAllSourcesOfBeacon(beacon.get().id())) {
            sources.put(source.id(), source);
        }

        Map<UUID, String> tapNames = Maps.newHashMap();
        for (Tap tap : nzyme.getTapManager().findAllTapsOfTenant(organizationId, tenantId)) {
            tapNames.put(tap.uuid(), tap.name());
        }

        Map<Long, L4AddressResponse> sourceAddresses = Maps.newHashMap();

        long total = beacons.countCheckins(beacon.get().id(), sourceId, filters, timeRange);
        List<BeaconCheckinResponse> result = Lists.newArrayList();
        for (BeaconCheckinEntry checkin : beacons.findCheckins(
                beacon.get().id(), sourceId, filters, timeRange, limit, offset)) {
            BeaconSourceEntry source = sources.get(checkin.beaconSourceId());
            if (source == null) {
                continue;
            }

            L4AddressResponse sourceAddress = sourceAddresses.computeIfAbsent(source.id(), id ->
                    addressResponse(source.sourceAddress(), null, source.sourceMac(), null, organizationId, tenantId));

            result.add(BeaconCheckinResponse.create(
                    checkin.timestamp(),
                    source.uuid(),
                    source.sourceAddress(),
                    source.sourceMac(),
                    checkin.destinationAddress(),
                    checkin.destinationMac(),
                    macResponse(checkin.destinationMac(), organizationId, tenantId),
                    sourceAddress,
                    addressResponse(checkin.destinationAddress(), beacon.get().destinationPort(), checkin.destinationMac(),
                            beacon.get().type(), organizationId, tenantId),
                    checkin.tapUuid(),
                    checkin.tapUuid() == null ? null : tapNames.get(checkin.tapUuid()),
                    checkin.sessionKey(),
                    checkin.l4Type(),
                    checkin.sessionCount(),
                    checkin.bytesRx(),
                    checkin.bytesTx(),
                    checkin.bytesTotal()
            ));
        }

        return Response.ok(BeaconCheckinsListResponse.create(total, result)).build();
    }

    @GET
    @Path("/show/{beacon_uuid}/notes")
    @Operation(operationId = "findBeaconNotes", summary = "List notes of a beacon",
            description = "Returns the operator notes of a beacon, most recent first.")
    @ApiResponse(responseCode = "200", description = "Notes found.",
            content = @Content(schema = @Schema(implementation = BeaconNotesListResponse.class)))
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response notes(@Parameter(hidden = true) @Context SecurityContext sc,
                          @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                          @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                          @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<BeaconNoteResponse> notes = Lists.newArrayList();
        for (BeaconNoteEntry note : beacons.findNotesOfBeacon(beacon.get().id())) {
            notes.add(BeaconNoteResponse.create(
                    note.uuid(), note.authorName(), note.authorEmail(), note.note(), note.createdAt()
            ));
        }

        return Response.ok(BeaconNotesListResponse.create(notes)).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{beacon_uuid}/notes")
    @Operation(operationId = "createBeaconNote", summary = "Add a note to a beacon",
            description = "Adds an operator note to a beacon. The calling user is recorded as the author. Requires "
                    + "the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "201", description = "Note created.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The note is empty or too long.", content = @Content)
    public Response createNote(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                               @RequestBody(description = "The note.", required = true, content = @Content(mediaType = "application/json"))
                               @Valid CreateBeaconNoteRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String note = req.note() == null ? "" : req.note().trim();
        if (note.isEmpty() || note.length() > 10000) {
            return Response.status(422).build();
        }

        AuthenticatedUser user = getAuthenticatedUser(sc);
        beacons.createNote(beacon.get().id(), user.getUserId(), user.getName(), user.getEmail(), note);

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{beacon_uuid}/notes/{note_uuid}")
    @Operation(operationId = "deleteBeaconNote", summary = "Delete a note of a beacon",
            description = "Deletes one operator note. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "204", description = "Note deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, note, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteNote(@Parameter(hidden = true) @Context SecurityContext sc,
                               @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                               @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                               @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                               @Parameter(description = "Note UUID.") @PathParam("note_uuid") UUID noteUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<BeaconNoteEntry> note = beacons.findNoteOfBeacon(noteUuid, beacon.get().id());
        if (note.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        beacons.deleteNote(note.get().id());

        return Response.noContent().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{beacon_uuid}")
    @Operation(operationId = "updateBeacon", summary = "Rename a beacon or change its description",
            description = "Sets the name and description of a beacon. Names must be unique within the tenant. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Beacon updated.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "409", description = "Another beacon of the tenant already has this name.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The name is empty or too long.", content = @Content)
    public Response update(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                           @RequestBody(description = "New name and description.", required = true, content = @Content(mediaType = "application/json"))
                           @Valid UpdateBeaconRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        Optional<BeaconEntry> beacon = beacons.findBeacon(beaconUuid, organizationId, tenantId);
        if (beacon.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String name = req.name().trim();
        if (name.isEmpty() || name.length() > 128) {
            return Response.status(422).build();
        }

        String description = req.description() == null || req.description().trim().isEmpty()
                ? null : req.description().trim();

        if (!name.equals(beacon.get().name())) {
            boolean taken = nzyme.getDatabase().withHandle(handle ->
                    beacons.beaconNameExists(handle, name, organizationId, tenantId));
            if (taken) {
                return Response.status(Response.Status.CONFLICT).build();
            }
        }

        beacons.updateBeacon(beaconUuid, name, description, organizationId, tenantId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{beacon_uuid}/classify/{classification}")
    @Operation(operationId = "classifyBeacon", summary = "Classify a beacon",
            description = "Sets the classification of a beacon to UNKNOWN, BENIGN, SUSPICIOUS or MALICIOUS. Depending "
                    + "on the tenant configuration, activity on suspicious and malicious beacons raises detection "
                    + "alerts. Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Beacon classified.", content = @Content)
    @ApiResponse(responseCode = "400", description = "Unknown classification.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response classify(@Parameter(hidden = true) @Context SecurityContext sc,
                             @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                             @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                             @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid,
                             @Parameter(description = "New classification: UNKNOWN, BENIGN, SUSPICIOUS or MALICIOUS.") @PathParam("classification") String classificationParam) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BeaconClassification classification;
        try {
            classification = BeaconClassification.valueOf(classificationParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        if (beacons.findBeacon(beaconUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        beacons.classifyBeacon(beaconUuid, classification, organizationId, tenantId);

        return Response.ok().build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/classify/{classification}")
    @Operation(operationId = "classifyBeacons", summary = "Classify all beacons matching filters",
            description = "Sets the classification of every beacon of the tenant that matches the given filters to "
                    + "UNKNOWN, BENIGN, SUSPICIOUS or MALICIOUS. Without filters, all beacons of the tenant are "
                    + "classified. Beacons that already have the classification are left untouched. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Beacons classified.",
            content = @Content(schema = @Schema(implementation = BeaconBulkClassificationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown classification.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response classifyAll(@Parameter(hidden = true) @Context SecurityContext sc,
                                @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                @Parameter(description = "New classification: UNKNOWN, BENIGN, SUSPICIOUS or MALICIOUS.") @PathParam("classification") String classificationParam,
                                @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder. Same fields as the beacons list. Omit to classify all beacons of the tenant.") @QueryParam("filters") @Nullable String filtersParameter,
                                @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker. Only beacons active at any point inside the range are classified. Omit for all time.") @QueryParam("time_range") @Valid @Nullable String timeRangeParameter) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BeaconClassification classification;
        try {
            classification = BeaconClassification.valueOf(classificationParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Filters filters = parseFiltersQueryParameter(filtersParameter);
        TimeRange timeRange = timeRangeParameter == null || timeRangeParameter.isEmpty()
                ? TimeRangeFactory.allTime() : parseTimeRangeQueryParameter(timeRangeParameter);

        int updated = nzyme.getEthernet().beacons().classifyBeacons(filters, timeRange, classification, organizationId, tenantId);

        return Response.ok(BeaconBulkClassificationResponse.create(updated)).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/show/{beacon_uuid}")
    @Operation(operationId = "deleteBeacon", summary = "Delete a beacon",
            description = "Deletes a beacon with all of its sources and check-ins. The beacon is detected again "
                    + "under a new random name if the devices keep calling out. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Beacon deleted.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Beacon, organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response delete(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                           @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                           @Parameter(description = "Beacon UUID.") @PathParam("beacon_uuid") UUID beaconUuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Beacons beacons = nzyme.getEthernet().beacons();

        if (beacons.findBeacon(beaconUuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        beacons.deleteBeacon(beaconUuid, organizationId, tenantId);

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}")
    @Operation(operationId = "deleteAllBeacons", summary = "Delete all beacons of a tenant",
            description = "Deletes every beacon of the tenant with all sources, check-ins and notes. Exclusions and "
                    + "the detection configuration are kept. Beacons are detected again under new random names if "
                    + "the devices keep calling out, unless an exclusion matches them. Requires the "
                    + "ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Beacons deleted.",
            content = @Content(schema = @Schema(implementation = BeaconsDeletedResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response deleteAll(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                              @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        int deleted = nzyme.getEthernet().beacons().deleteAllBeacons(organizationId, tenantId);

        return Response.ok(BeaconsDeletedResponse.create(deleted)).build();
    }

    private static final RegistryKey[] CONFIGURATION_KEYS = {
            BeaconRegistryKeys.DETECTION_ENABLED,
            BeaconRegistryKeys.INCLUDE_SITE_LOCAL_DESTINATIONS,
            BeaconRegistryKeys.DETECTION_WINDOW_MINUTES,
            BeaconRegistryKeys.MINIMUM_CHECKINS,
            BeaconRegistryKeys.MINIMUM_INTERVAL_SECONDS,
            BeaconRegistryKeys.DNS_MINIMUM_INTERVAL_SECONDS,
            BeaconRegistryKeys.MAXIMUM_JITTER_PERCENT,
            BeaconRegistryKeys.MINIMUM_REGULARITY_PERCENT,
            BeaconRegistryKeys.CHECKIN_RETENTION_TIME_DAYS,
            BeaconRegistryKeys.SOURCE_TLS_ENABLED,
            BeaconRegistryKeys.SOURCE_HTTP_ENABLED,
            BeaconRegistryKeys.SOURCE_SSH_ENABLED,
            BeaconRegistryKeys.SOURCE_SOCKS_ENABLED,
            BeaconRegistryKeys.SOURCE_DNS_ENABLED,
            BeaconRegistryKeys.SOURCE_TCP_ENABLED,
            BeaconRegistryKeys.SOURCE_UDP_ENABLED,
            BeaconRegistryKeys.ALERT_ON_NEW_BEACON,
            BeaconRegistryKeys.ALERT_ON_NEW_SOURCE,
            BeaconRegistryKeys.ALERT_ON_SUSPICIOUS_ACTIVITY,
            BeaconRegistryKeys.ALERT_ON_MALICIOUS_ACTIVITY
    };

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "findBeaconsConfiguration", summary = "Get the beacon detection configuration of a tenant",
            description = "Returns the beacon detection settings of the tenant in three groups: detection "
                    + "parameters (window, thresholds, check-in retention), enabled data sources and alerting. "
                    + "Requires the ethernet_monitoring_manage feature permission.")
    @ApiResponse(responseCode = "200", description = "Configuration found.",
            content = @Content(schema = @Schema(implementation = BeaconsConfigurationResponse.class)))
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response getConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ConfigurationEntryResponse> detection = Lists.newArrayList();
        detection.add(booleanEntry(BeaconRegistryKeys.DETECTION_ENABLED, "Beacon detection enabled", organizationId, tenantId));
        detection.add(booleanEntry(BeaconRegistryKeys.INCLUDE_SITE_LOCAL_DESTINATIONS, "Include site-local destinations", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.DETECTION_WINDOW_MINUTES, "Detection window (Minutes)", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.MINIMUM_CHECKINS, "Minimum check-ins in window", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.MINIMUM_INTERVAL_SECONDS, "Minimum interval (Seconds)", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.DNS_MINIMUM_INTERVAL_SECONDS, "Minimum interval for DNS beacons (Seconds)", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.MAXIMUM_JITTER_PERCENT, "Maximum jitter (Percent)", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.MINIMUM_REGULARITY_PERCENT, "Minimum regularity (Percent)", organizationId, tenantId));
        detection.add(numberEntry(BeaconRegistryKeys.CHECKIN_RETENTION_TIME_DAYS, "Check-in retention time (Days)", organizationId, tenantId));

        List<ConfigurationEntryResponse> sources = Lists.newArrayList();
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_TLS_ENABLED, "TLS sessions", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_HTTP_ENABLED, "HTTP sessions", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_SSH_ENABLED, "SSH sessions", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_SOCKS_ENABLED, "SOCKS tunnels", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_DNS_ENABLED, "DNS queries", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_TCP_ENABLED, "Other TCP sessions", organizationId, tenantId));
        sources.add(booleanEntry(BeaconRegistryKeys.SOURCE_UDP_ENABLED, "Other UDP sessions", organizationId, tenantId));

        List<ConfigurationEntryResponse> alerting = Lists.newArrayList();
        alerting.add(booleanEntry(BeaconRegistryKeys.ALERT_ON_NEW_BEACON, "Alert on new beacon", organizationId, tenantId));
        alerting.add(booleanEntry(BeaconRegistryKeys.ALERT_ON_NEW_SOURCE, "Alert on new device joining a known beacon", organizationId, tenantId));
        alerting.add(booleanEntry(BeaconRegistryKeys.ALERT_ON_SUSPICIOUS_ACTIVITY, "Alert on activity of suspicious beacons", organizationId, tenantId));
        alerting.add(booleanEntry(BeaconRegistryKeys.ALERT_ON_MALICIOUS_ACTIVITY, "Alert on activity of malicious beacons", organizationId, tenantId));

        return Response.ok(BeaconsConfigurationResponse.create(detection, sources, alerting)).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { MANAGE_PERMISSION })
    @Path("/organization/{organization_id}/tenant/{tenant_id}/configuration")
    @Operation(operationId = "updateBeaconsConfiguration", summary = "Update the beacon detection configuration of a tenant",
            description = "Updates one or more beacon detection settings of the tenant. Accepted keys are the "
                    + "keys returned by the configuration endpoint. Requires the ethernet_monitoring_manage feature "
                    + "permission.")
    @ApiResponse(responseCode = "200", description = "Configuration updated.", content = @Content)
    @ApiResponse(responseCode = "400", description = "The change contains a key that is not part of the beacon configuration.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change is empty or a value violates the constraints of its configuration key.", content = @Content)
    public Response setConfiguration(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Organization UUID.") @PathParam("organization_id") UUID organizationId,
                                     @Parameter(description = "Tenant UUID.") @PathParam("tenant_id") UUID tenantId,
                                     @RequestBody(description = "Map of configuration keys and their new values, wrapped in a change field.", required = true, content = @Content(mediaType = "application/json"))
                                     @Valid GenericConfigurationUpdateRequest req) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (req.change().isEmpty()) {
            return Response.status(422).build();
        }

        Optional<List<RegistryChangeValidator.Change>> changes = RegistryChangeValidator
                .allowing(CONFIGURATION_KEYS)
                .validate(req.change());

        if (changes.isEmpty()) {
            return Response.status(422).build();
        }

        for (RegistryChangeValidator.Change c : changes.get()) {
            nzyme.getDatabaseCoreRegistry().setValue(c.key(), c.value(), organizationId, tenantId);
        }

        return Response.ok().build();
    }

    private ConfigurationEntryResponse booleanEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().beacons().booleanValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.BOOLEAN,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-beacons-config"
        );
    }

    private ConfigurationEntryResponse numberEntry(RegistryKey key, String title, UUID organizationId, UUID tenantId) {
        return ConfigurationEntryResponse.create(
                key.key(),
                title,
                nzyme.getEthernet().beacons().intValue(key, organizationId, tenantId),
                ConfigurationEntryValueType.NUMBER,
                key.defaultValue().orElse(null),
                key.requiresRestart(),
                key.constraints().orElse(Collections.emptyList()),
                "ethernet-beacons-config"
        );
    }

    private BeaconDetailsResponse toResponse(BeaconEntry beacon,
                                             UUID organizationId,
                                             UUID tenantId,
                                             Map<BeaconExclusionEntry, BeaconExclusionMatcher> exclusions) {
        List<BeaconExclusionReferenceResponse> matchingExclusions = Lists.newArrayList();
        for (BeaconExclusionEntry exclusion : nzyme.getEthernet().beacons().exclusions()
                .findMatchingExclusions(beacon, exclusions)) {
            matchingExclusions.add(BeaconExclusionReferenceResponse.create(exclusion.uuid(), exclusion.name()));
        }

        Map<String, Object> enrichment = null;
        if (beacon.enrichment() != null) {
            try {
                enrichment = om.readValue(beacon.enrichment(), new TypeReference<>() {});
            } catch (Exception e) {
                LOG.error("Could not parse enrichment of beacon [{}].", beacon.uuid(), e);
            }
        }

        String typeHumanReadable;
        try {
            typeHumanReadable = BeaconType.valueOf(beacon.type()).getHumanReadable();
        } catch (IllegalArgumentException e) {
            typeHumanReadable = beacon.type();
        }

        return BeaconDetailsResponse.create(
                beacon.uuid(),
                beacon.organizationId(),
                beacon.tenantId(),
                beacon.type(),
                typeHumanReadable,
                beacon.destinationKey(),
                beacon.destinationHost(),
                beacon.destinationAddress(),
                beacon.destinationPort(),
                BeaconDetector.destinationDescription(beacon),
                addressResponse(beacon.destinationAddress(), beacon.destinationPort(), null, beacon.type(),
                        organizationId, tenantId),
                beacon.destinationIsSiteLocal(),
                beacon.name(),
                beacon.description(),
                beacon.classification(),
                beacon.classificationChangedAt(),
                beacon.sourceCount(),
                beacon.checkinCount(),
                beacon.intervalSeconds(),
                beacon.intervalJitter(),
                enrichment,
                beacon.firstSeen(),
                beacon.lastSeen(),
                beacon.createdAt(),
                beacon.updatedAt(),
                matchingExclusions
        );
    }

    private BeaconSourceResponse toResponse(BeaconSourceEntry source, UUID organizationId, UUID tenantId) {
        String oui = null;
        UUID assetUuid = null;
        String assetName = null;
        if (source.sourceMac() != null) {
            oui = nzyme.getOuiService().lookup(source.sourceMac()).orElse(null);
            assetUuid = nzyme.getAssetsManager().findAssetByMac(source.sourceMac(), organizationId, tenantId)
                    .map(AssetEntry::uuid).orElse(null);
            assetName = nzyme.getContextService().findMacAddressContext(source.sourceMac(), organizationId, tenantId)
                    .map(MacAddressContextEntry::name).orElse(null);
        }

        return BeaconSourceResponse.create(
                source.uuid(),
                source.sourceAddress(),
                addressResponse(source.sourceAddress(), null, source.sourceMac(), null, organizationId, tenantId),
                source.sourceMac(),
                oui,
                assetUuid,
                assetName,
                source.checkinCount(),
                source.intervalSeconds(),
                source.intervalJitter(),
                source.regularity(),
                source.firstSeen(),
                source.lastSeen()
        );
    }

    @Nullable
    private EthernetMacAddressResponse macResponse(@Nullable String mac, UUID organizationId, UUID tenantId) {
        if (mac == null) {
            return null;
        }

        Optional<AssetEntry> asset = nzyme.getAssetsManager().findAssetByMac(mac, organizationId, tenantId);
        Optional<MacAddressContextEntry> context = nzyme.getContextService()
                .findMacAddressContext(mac, organizationId, tenantId);

        return EthernetMacAddressResponse.create(
                mac,
                nzyme.getOuiService().lookup(mac).orElse(null),
                asset.map(AssetEntry::uuid).orElse(null),
                asset.map(AssetEntry::isActive).orElse(null),
                RestHelpers.macContextEntryToResponse(context)
        );
    }

    @Nullable
    private L4AddressResponse addressResponse(@Nullable String address,
                                              @Nullable Integer port,
                                              @Nullable String mac,
                                              @Nullable String beaconType,
                                              UUID organizationId,
                                              UUID tenantId) {
        if (address == null || !BeaconDetector.isValidAddress(address)) {
            return null;
        }

        InetAddress inet;
        try {
            inet = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }

        GeoData geo = null;
        Optional<GeoIpLookupResult> lookup = nzyme.getGeoIpService().lookup(inet);
        if (lookup.isPresent() && (lookup.get().asn() != null || lookup.get().geo() != null)) {
            GeoIpLookupResult r = lookup.get();
            geo = GeoData.create(
                    r.asn() == null ? null : r.asn().number(),
                    r.asn() == null ? null : r.asn().name(),
                    r.asn() == null ? null : r.asn().domain(),
                    r.geo() == null ? null : r.geo().city(),
                    r.geo() == null ? null : r.geo().countryCode(),
                    r.geo() == null ? null : r.geo().latitude(),
                    r.geo() == null ? null : r.geo().longitude()
            );
        }

        L4AddressAttributes attributes = L4AddressAttributes.create(
                inet.isSiteLocalAddress(), inet.isLoopbackAddress(), inet.isMulticastAddress()
        );

        L4Type l4Type = BeaconType.UDP.name().equals(beaconType) || BeaconType.DNS.name().equals(beaconType)
                ? L4Type.UDP : L4Type.TCP;

        try {
            return RestHelpers.L4AddressDataToResponse(nzyme, organizationId, tenantId, l4Type,
                    L4AddressData.create(mac, address, port, geo, attributes));
        } catch (Exception e) {
            LOG.warn("Could not build address data for beacon address [{}].", address, e);
            return null;
        }
    }

}
