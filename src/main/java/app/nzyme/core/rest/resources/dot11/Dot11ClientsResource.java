package app.nzyme.core.rest.resources.dot11;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.Dot11RegistryKeys;
import app.nzyme.core.dot11.db.*;
import app.nzyme.core.rest.RestHelpers;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.misc.CategorizedTransparentContextData;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressContextResponse;
import app.nzyme.core.rest.responses.dot11.Dot11MacAddressResponse;
import app.nzyme.core.rest.responses.shared.TapBasedSignalStrengthResponse;
import app.nzyme.core.rest.responses.dot11.clients.*;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.TimeRangeFactory;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;

import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/dot11/clients")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Clients", description = "WiFi clients that taps recorded on the air. A client is connected if it was "
        + "seen exchanging frames with an access point, and disconnected if it was only seen probing or sending "
        + "frames that are not tied to a BSSID.")
public class Dot11ClientsResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/connected")
    @Operation(operationId = "findDot11ConnectedClients", summary = "List connected clients",
            description = "Returns all clients that the selected taps saw connected to an access point in the time "
                    + "range, including the BSSID they were connected to and their probe requests. Sorts by last seen "
                    + "descending unless you pass a sorting column and direction.")
    @ApiResponse(responseCode = "200", description = "Clients found.",
            content = @Content(schema = @Schema(implementation = ConnectedClientListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    public Response connectedClients(@Parameter(hidden = true) @Context SecurityContext sc,
                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                     @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                     @Parameter(description = "Sorting column. Omit to sort by last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                     @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                     @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                     @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Dot11.ClientOrderColumn orderColumn = Dot11.ClientOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Dot11.ClientOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        // Connected clients.
        long connectedCount = nzyme.getDot11().countBSSIDClients(timeRange, filters, tapUuids);
        List<ConnectedClientDetailsResponse> connectedClients = Lists.newArrayList();

        for (ConnectedClientDetails client : nzyme.getDot11().findBSSIDClients(
                timeRange, filters, tapUuids, limit, offset, orderColumn, orderDirection)) {
            Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                    client.clientMac(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            List<String> probeRequests = nzyme.getDot11()
                    .findProbeRequestsOfClient(client.clientMac(), tapUuids);

            Optional<MacAddressContextEntry> clientBssidContext = nzyme.getContextService().findMacAddressContext(
                    client.bssid(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            connectedClients.add(ConnectedClientDetailsResponse.create(
                    Dot11MacAddressResponse.create(
                            client.clientMac(),
                            nzyme.getOuiService().lookup(client.clientMac()).orElse(null),
                            Tools.macAddressIsRandomized(client.clientMac()),
                            clientContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ),
                    client.lastSeen(),
                    Dot11MacAddressResponse.create(
                            client.bssid(),
                            nzyme.getOuiService().lookup(client.bssid()).orElse(null),
                            null,
                            clientBssidContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ),
                    probeRequests,
                    client.signalStrength()
            ));
        }

        return Response.ok(ConnectedClientListResponse.create(connectedCount, connectedClients)).build();
    }

    @GET
    @Path("/disconnected")
    @Operation(operationId = "findDot11DisconnectedClients", summary = "List disconnected clients",
            description = "Returns all clients that the selected taps recorded in the time range without seeing them "
                    + "connected to an access point. Clients that were connected at any point in the time range are "
                    + "left out. Sorts by last seen descending unless you pass a sorting column and direction.")
    @ApiResponse(responseCode = "200", description = "Clients found.",
            content = @Content(schema = @Schema(implementation = DisconnectedClientListResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown sorting column or direction.", content = @Content)
    public Response disconnectedClients(@Parameter(hidden = true) @Context SecurityContext sc,
                                        @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                        @Parameter(description = "Set to true to leave out clients with a randomized MAC address.") @QueryParam("skip_randomized") boolean skipRandomized,
                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                        @Parameter(description = "Sorting column. Omit to sort by last seen.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                        @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Dot11.ClientOrderColumn orderColumn = Dot11.ClientOrderColumn.LAST_SEEN;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Dot11.ClientOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        List<String> macAddressesOfAllConnectedClients = nzyme.getDot11()
                .findMacAddressesOfAllBSSIDClients(timeRange, tapUuids);

        // Disconnected clients.
        long disconnectedCount = nzyme.getDot11().countClients(timeRange, filters, skipRandomized, tapUuids);
        List<DisconnectedClientDetailsResponse> disconnectedClients = Lists.newArrayList();

        for (DisconnectedClientDetails client : nzyme.getDot11().findClients(
                timeRange, filters, tapUuids, macAddressesOfAllConnectedClients, skipRandomized, limit, offset,
                orderColumn, orderDirection)) {
            Optional<MacAddressContextEntry> clientContext = nzyme.getContextService().findMacAddressContext(
                    client.clientMac(),
                    authenticatedUser.getOrganizationId(),
                    authenticatedUser.getTenantId()
            );

            disconnectedClients.add(DisconnectedClientDetailsResponse.create(
                    Dot11MacAddressResponse.create(
                            client.clientMac(),
                            nzyme.getOuiService().lookup(client.clientMac()).orElse(null),
                            Tools.macAddressIsRandomized(client.clientMac()),
                            clientContext.map(macAddressContextEntry ->
                                            Dot11MacAddressContextResponse.create(
                                                    macAddressContextEntry.name(),
                                                    macAddressContextEntry.description(),
                                                    macAddressContextEntry.notes()
                                            ))
                                    .orElse(null)
                    ),
                    client.lastSeen(),
                    client.probeRequests(),
                    client.signalStrength()
            ));
        }

        return Response.ok(DisconnectedClientListResponse.create(disconnectedCount, disconnectedClients)).build();
    }

    @GET
    @Path("/connected/histogram")
    @Operation(operationId = "findDot11ConnectedClientsHistogram", summary = "Get connected client histogram",
            description = "Returns the number of distinct connected clients per time bucket. The bucket size is "
                    + "derived from the time range. Buckets without data are missing from the result.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ClientHistogramResponse.class)))
    public Response connectedHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                       @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                       @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        Map<DateTime, ClientHistogramValueResponse> connected = Maps.newTreeMap();
        for (ClientHistogramEntry entry : nzyme.getDot11()
                .getConnectedClientHistogram(timeRange, filters, bucketing, tapUuids)) {
            connected.put(entry.bucket(), ClientHistogramValueResponse.create(
                    entry.bucket(), entry.clientCount()
            ));
        }

        return Response.ok(ClientHistogramResponse.create(connected)).build();
    }

    @GET
    @Path("/disconnected/histogram")
    @Operation(operationId = "findDot11DisconnectedClientsHistogram", summary = "Get disconnected client histogram",
            description = "Returns the number of distinct disconnected clients per time bucket. Clients that were "
                    + "connected at any point in the time range are left out. Buckets without data are missing from "
                    + "the result.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = ClientHistogramResponse.class)))
    public Response disconnectedHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                          @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                          @Parameter(description = "Set to true to leave out clients with a randomized MAC address.") @QueryParam("skip_randomized") boolean skipRandomized,
                                          @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                          @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        List<String> macAddressesOfAllConnectedClients = nzyme.getDot11()
                .findMacAddressesOfAllBSSIDClients(timeRange, tapUuids);

        Map<DateTime, ClientHistogramValueResponse> disconnected = Maps.newTreeMap();
        for (ClientHistogramEntry entry : nzyme.getDot11().getDisconnectedClientHistogram(
                timeRange, filters, skipRandomized, bucketing, tapUuids, macAddressesOfAllConnectedClients)) {
            disconnected.put(entry.bucket(), ClientHistogramValueResponse.create(
                    entry.bucket(), entry.clientCount()
            ));;
        }

        return Response.ok(ClientHistogramResponse.create(disconnected)).build();
    }

    @GET
    @Path("/show/{clientMac}")
    @Operation(operationId = "findDot11Client", summary = "Get details of a WiFi client",
            description = "Merges the connected and disconnected view of one client, including its BSSID history, "
                    + "probe requests and the IP addresses and hostnames from transparent context. The signal "
                    + "strength per tap covers the last 15 minutes.")
    @ApiResponse(responseCode = "200", description = "Client found.",
            content = @Content(schema = @Schema(implementation = ClientDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "No tap the user can access recorded this client.", content = @Content)
    public Response client(@Parameter(hidden = true) @Context SecurityContext sc,
                           @Parameter(description = "Client MAC address.") @PathParam("clientMac") @NotEmpty String clientMac,
                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        Optional<ClientDetails> client = nzyme.getDot11().findMergedConnectedOrDisconnectedClient(
                clientMac, tapUuids, null, null, authenticatedUser
        );

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ClientDetails c = client.get();

        // Recent signal strength by tap.
        List<TapBasedSignalStrengthResponse> connectedSignalStrengthsByTap = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ssr : nzyme.getDot11()
                .findBssidClientSignalStrengthPerTap(c.mac(), TimeRangeFactory.fifteenMinutes(), tapUuids)) {
            connectedSignalStrengthsByTap.add(TapBasedSignalStrengthResponse.create(
                    ssr.tapUuid(), ssr.tapName(), ssr.signalStrength()
            ));
        }
        List<TapBasedSignalStrengthResponse> disconnectedSignalStrengthsByTap = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ssr : nzyme.getDot11()
                .findDisconnectedClientSignalStrengthPerTap(c.mac(),  TimeRangeFactory.fifteenMinutes(), tapUuids)) {
            disconnectedSignalStrengthsByTap.add(TapBasedSignalStrengthResponse.create(
                    ssr.tapUuid(), ssr.tapName(), ssr.signalStrength()
            ));
        }

        Optional<MacAddressContextEntry> macContext = nzyme.getContextService().findMacAddressContext(
                c.mac(),
                authenticatedUser.getOrganizationId(),
                authenticatedUser.getTenantId()
        );

        CategorizedTransparentContextData transparentContext;
        if (macContext.isPresent()) {
            transparentContext =  RestHelpers.transparentContextDataToResponses(
                    nzyme.getContextService().findTransparentMacAddressContext(macContext.get().id())
            );
        } else {
            transparentContext = CategorizedTransparentContextData.create(Lists.newArrayList(), Lists.newArrayList());
        }

        return Response.ok(ClientDetailsResponse.create(
                Dot11MacAddressResponse.create(
                        c.mac(),
                        nzyme.getOuiService().lookup(c.mac()).orElse(null),
                        Tools.macAddressIsRandomized(c.mac()),
                        macContext.map(macAddressContextEntry ->
                                        Dot11MacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description(),
                                                macAddressContextEntry.notes()
                                        ))
                                .orElse(null)
                ),
                c.connectedBSSID(),
                c.connectedBSSIDHistory(),
                c.firstSeen(),
                c.lastSeen(),
                transparentContext.ipAddresses(),
                transparentContext.hostnames(),
                c.probeRequests(),
                connectedSignalStrengthsByTap,
                disconnectedSignalStrengthsByTap
        )).build();
    }

    @GET
    @Path("/show/{clientMac}/histogram/signal/connected")
    @Operation(operationId = "findDot11ClientConnectedSignalHistogram",
            summary = "Get connected signal strength histogram of a client",
            description = "Returns the average signal strength of the client while it was connected to an access "
                    + "point, per time bucket. Exactly one tap must be selected because signal strength is only "
                    + "comparable within a single tap.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientSignalStrengthResponse.class))))
    @ApiResponse(responseCode = "400", description = "Not exactly one tap was selected.", content = @Content)
    @ApiResponse(responseCode = "404", description = "No tap the user can access recorded this client.", content = @Content)
    public Response connectedSignalStrengthHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                     @Parameter(description = "Client MAC address.") @PathParam("clientMac") @NotEmpty String clientMac,
                                                     @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                     @Parameter(description = "UUID of exactly one tap to include.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (tapUuids.size() != 1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        UUID tapUUID = tapUuids.get(0);

        Optional<ClientDetails> client = nzyme.getDot11().findMergedConnectedOrDisconnectedClient(
                clientMac, tapUuids, null, null, authenticatedUser
        );

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ClientSignalStrengthResponse> histogram = Lists.newArrayList();
        for (ClientSignalStrengthResult r : nzyme.getDot11()
                .findBssidClientSignalStrengthHistogram(clientMac, timeRange, Bucketing.getConfig(timeRange), tapUUID)) {
            histogram.add(ClientSignalStrengthResponse.create(r.bucket(), r.signalStrength().longValue()));
        }

        return Response.ok(histogram).build();
    }

    @GET
    @Path("/show/{clientMac}/histogram/signal/disconnected")
    @Operation(operationId = "findDot11ClientDisconnectedSignalHistogram",
            summary = "Get disconnected signal strength histogram of a client",
            description = "Returns the average signal strength of the client while it was not connected to an access "
                    + "point, per time bucket. Exactly one tap must be selected because signal strength is only "
                    + "comparable within a single tap.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientSignalStrengthResponse.class))))
    @ApiResponse(responseCode = "400", description = "Not exactly one tap was selected.", content = @Content)
    @ApiResponse(responseCode = "404", description = "No tap the user can access recorded this client.", content = @Content)
    public Response disconnectedSignalStrengthHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                                        @Parameter(description = "Client MAC address.") @PathParam("clientMac") @NotEmpty String clientMac,
                                                        @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                                        @Parameter(description = "UUID of exactly one tap to include.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        if (tapUuids.size() != 1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        UUID tapUUID = tapUuids.get(0);

        Optional<ClientDetails> client = nzyme.getDot11().findMergedConnectedOrDisconnectedClient(
                clientMac, tapUuids, null, null, authenticatedUser
        );

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<ClientSignalStrengthResponse> histogram = Lists.newArrayList();
        for (ClientSignalStrengthResult r : nzyme.getDot11().findDisconnectedClientSignalStrengthHistogram(
                clientMac, timeRange, Bucketing.getConfig(timeRange), tapUUID)) {
            histogram.add(ClientSignalStrengthResponse.create(
                    r.bucket(), r.signalStrength().longValue()
            ));
        }

        return Response.ok(histogram).build();
    }


    @GET
    @Path("/show/{clientMac}/histogram/frames")
    @Operation(operationId = "findDot11ClientFrameHistogram", summary = "Get frame count histogram of a client",
            description = "Returns an object keyed by bucket timestamp. Each value holds the total frame count of the "
                    + "client in that bucket, split into connected frames, disconnected frames and disconnection "
                    + "frames. Buckets without data are missing from the result.")
    @ApiResponse(responseCode = "200", description = "Histogram found. The response is an object keyed by bucket timestamp.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "404", description = "No tap the user can access recorded this client.", content = @Content)
    public Response clientFrameCountHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                              @Parameter(description = "Client MAC address.") @PathParam("clientMac") @NotEmpty String clientMac,
                                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        Optional<ClientDetails> client = nzyme.getDot11().findMergedConnectedOrDisconnectedClient(
                clientMac, tapUuids, timeRange, Bucketing.getConfig(timeRange), authenticatedUser
        );

        if (client.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ClientDetails c = client.get();

        List<DiscoHistogramEntry> discos = nzyme.getDot11().getDiscoHistogram(
                Dot11.DiscoType.DISCONNECTION,  timeRange, Bucketing.getConfig(timeRange), tapUuids, List.of(c.mac())
        );

        Map<DateTime, Map<String, Long>> tempValues = Maps.newHashMap();
        for (ClientActivityHistogramEntry ce : c.connectedFramesHistogram()) {
            Map<String, Long> values = tempValues.get(ce.bucket());
            if (values == null) {
                Map<String, Long> newValues = Maps.newHashMap();
                newValues.put("connected", ce.frames());
                tempValues.put(ce.bucket(), newValues);
            } else {
                values.compute("connected", (k, current) -> (current == null ? 0 : current) + ce.frames());
            }
        }

        for (ClientActivityHistogramEntry ce : c.disconnectedFramesHistogram()) {
            Map<String, Long> values = tempValues.get(ce.bucket());
            if (values == null) {
                Map<String, Long> newValues = Maps.newHashMap();
                newValues.put("disconnected", ce.frames());
                tempValues.put(ce.bucket(), newValues);
            } else {
                values.compute("disconnected", (k, current) -> (current == null ? 0 : current) + ce.frames());
            }
        }

        for (DiscoHistogramEntry disco : discos) {
            Map<String, Long> values = tempValues.get(disco.bucket());
            if (values == null) {
                Map<String, Long> newValues = Maps.newHashMap();
                newValues.put("disco", disco.frameCount());
                tempValues.put(disco.bucket(), newValues);
            } else {
                values.compute("disco", (k, current) -> (current == null ? 0 : current) + disco.frameCount());
            }
        }

        Map<DateTime, ClientActivityHistogramValueResponse> activityHistogram = Maps.newTreeMap();
        for (Map.Entry<DateTime, Map<String, Long>> temp : tempValues.entrySet()) {
            long connectedFrames = temp.getValue().get("connected") == null ? 0L : temp.getValue().get("connected");
            long disconnectedFrames = temp.getValue().get("disconnected") == null ? 0L : temp.getValue().get("disconnected");
            long discoActivityFrames = temp.getValue().get("disco") == null ? 0L : temp.getValue().get("disco");

            activityHistogram.put(temp.getKey(), ClientActivityHistogramValueResponse.create(
                    temp.getKey(),
                    connectedFrames+disconnectedFrames+discoActivityFrames,
                    connectedFrames,
                    disconnectedFrames,
                    discoActivityFrames
            ));
        }

        return Response.ok(activityHistogram).build();
    }

}
