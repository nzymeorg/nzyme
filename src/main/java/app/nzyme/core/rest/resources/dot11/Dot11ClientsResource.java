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
public class Dot11ClientsResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/connected")
    public Response connectedClients(@Context SecurityContext sc,
                                     @QueryParam("time_range") @Valid String timeRangeParameter,
                                     @QueryParam("filters") String filtersParameter,
                                     @QueryParam("taps") String taps,
                                     @QueryParam("order_column") @Nullable String orderColumnParam,
                                     @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                     @QueryParam("limit") int limit,
                                     @QueryParam("offset") int offset) {
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
    public Response disconnectedClients(@Context SecurityContext sc,
                                        @QueryParam("filters") String filtersParameter,
                                        @QueryParam("skip_randomized") boolean skipRandomized,
                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                        @QueryParam("taps") String taps,
                                        @QueryParam("order_column") @Nullable String orderColumnParam,
                                        @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                        @QueryParam("limit") int limit,
                                        @QueryParam("offset") int offset) {
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
    public Response connectedHistogram(@Context SecurityContext sc,
                                       @QueryParam("time_range") @Valid String timeRangeParameter,
                                       @QueryParam("filters") String filtersParameter,
                                       @QueryParam("taps") String taps) {
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
    public Response disconnectedHistogram(@Context SecurityContext sc,
                                          @QueryParam("filters") String filtersParameter,
                                          @QueryParam("skip_randomized") boolean skipRandomized,
                                          @QueryParam("time_range") @Valid String timeRangeParameter,
                                          @QueryParam("taps") String taps) {
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
    public Response client(@Context SecurityContext sc,
                           @PathParam("clientMac") @NotEmpty String clientMac,
                           @QueryParam("taps") String taps) {
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
    public Response connectedSignalStrengthHistogram(@Context SecurityContext sc,
                                                     @PathParam("clientMac") @NotEmpty String clientMac,
                                                     @QueryParam("time_range") @Valid String timeRangeParameter,
                                                     @QueryParam("taps") String taps) {
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
    public Response disconnectedSignalStrengthHistogram(@Context SecurityContext sc,
                                                        @PathParam("clientMac") @NotEmpty String clientMac,
                                                        @QueryParam("time_range") @Valid String timeRangeParameter,
                                                        @QueryParam("taps") String taps) {
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
    public Response clientFrameCountHistogram(@Context SecurityContext sc,
                                              @PathParam("clientMac") @NotEmpty String clientMac,
                                              @QueryParam("time_range") @Valid String timeRangeParameter,
                                              @QueryParam("taps") String taps) {
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
