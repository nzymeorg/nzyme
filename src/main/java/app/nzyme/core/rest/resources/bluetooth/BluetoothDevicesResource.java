package app.nzyme.core.rest.resources.bluetooth;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.db.BluetoothDeviceSummary;
import app.nzyme.core.bluetooth.sig.BluetoothDeviceClass;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.rest.RestTools;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.responses.bluetooth.*;
import app.nzyme.core.rest.responses.shared.TapBasedSignalStrengthResponse;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.core.util.filters.Filters;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.nzyme.core.util.filters.FilterParser.parseFiltersQueryParameter;

@Path("/api/bluetooth/devices")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class BluetoothDevicesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response findAll(@Context SecurityContext sc,
                            @QueryParam("organization_id") UUID organizationId,
                            @QueryParam("tenant_id") UUID tenantId,
                            @QueryParam("time_range") @Valid String timeRangeParameter,
                            @QueryParam("order_column") @Nullable String orderColumnParam,
                            @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @QueryParam("filters") String filtersParameter,
                            @QueryParam("limit") int limit,
                            @QueryParam("offset") int offset,
                            @QueryParam("taps") String taps) {
        List<UUID> tapUuids = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Bluetooth.OrderColumn orderColumn = Bluetooth.OrderColumn.AVERAGE_RSSI;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = Bluetooth.OrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long total = nzyme.getBluetooth().countAllDevices(timeRange, filters, tapUuids);

        List<BluetoothDeviceSummaryDetailsResponse> devices = Lists.newArrayList();
        for (BluetoothDeviceSummary dev : nzyme.getBluetooth()
                .findAllDevices(timeRange, filters, orderColumn, orderDirection, limit, offset, tapUuids)) {
            devices.add(buildResponse(dev, organizationId, tenantId));
        }

        return Response.ok(BluetoothDeviceSummaryListResponse.create(total, devices)).build();
    }

    @GET
    @Path("/show/{mac}")
    public Response findOne(@Context SecurityContext sc,
                            @QueryParam("organization_id") UUID organizationId,
                            @QueryParam("tenant_id") UUID tenantId,
                            @PathParam("mac") @MacAddress String mac,
                            @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<BluetoothDeviceSummary> device = nzyme.getBluetooth().findOneDevice(mac, tapUuids);

        if (device.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(BluetoothDeviceDetailsResponse.create(
                buildResponse(device.get(), organizationId, tenantId)
        )).build();
    }

    @GET
    @Path("/show/{mac}/rssi/histogram")
    public Response rssiHistogram(@Context SecurityContext sc,
                                  @PathParam("mac") @MacAddress String mac,
                                  @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        List<GenericIntegerHistogramEntry> histo = nzyme.getBluetooth()
                .getDeviceSignalStrengthHistogram(mac, timeRange, bucketing, tapUuids);

        return Response.ok(RestTools.genericHistogramToResponse(histo)).build();
    }

    @GET
    @Path("/show/{mac}/rssi/bytap")
    public Response rssiByTap(@Context SecurityContext sc,
                              @PathParam("mac") @MacAddress String mac,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<TapBasedSignalStrengthResponse> response = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ss : nzyme.getBluetooth()
                .getDeviceSignalStrengthPerTap(mac, timeRange, tapUuids)) {
            response.add(TapBasedSignalStrengthResponse.create(
                    ss.tapUuid(),
                    ss.tapName(),
                    ss.signalStrength()
            ));
        }

        return Response.ok(response).build();
    }

    private BluetoothDeviceSummaryDetailsResponse buildResponse(BluetoothDeviceSummary dev,
                                                                UUID organizationId,
                                                                UUID tenantId) {
        Optional<MacAddressContextEntry> deviceContext = nzyme.getContextService().findMacAddressContext(
                dev.mac(),
                organizationId,
                tenantId
        );

        return BluetoothDeviceSummaryDetailsResponse.create(
                BluetoothMacAddressResponse.create(
                        dev.mac(),
                        nzyme.getOuiService().lookup(dev.mac()).orElse(null),
                        Tools.macAddressIsRandomized(dev.mac()),
                        deviceContext.map(macAddressContextEntry ->
                                        BluetoothMacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description()
                                        ))
                                .orElse(null)
                ),
                dev.ouis(),
                dev.aliases(),
                dev.devices(),
                dev.transports(),
                dev.names(),
                dev.averageRssi(),
                dev.manufacturerNames(),
                buildDeviceClasses(dev),
                dev.discoveredServices(),
                dev.tags(),
                dev.firstSeen(),
                dev.lastSeen()
        );
    }

    private static List<String> buildDeviceClasses(BluetoothDeviceSummary dev) {
        List<String> deviceClasses = Lists.newArrayList();
        for (Integer classNumber : dev.classNumbers()) {
            if (classNumber > 0) {
                BluetoothDeviceClass c = new BluetoothDeviceClass(classNumber);

                String minor = c.getMinorDeviceClass();
                String major = c.getMajorDeviceClass();

                if (minor != null) {
                    deviceClasses.add(minor);
                } else {
                    if (major != null) {
                        deviceClasses.add(major);
                    }
                }
            }
        }
        return deviceClasses;
    }

}
