package app.nzyme.core.rest.resources.bluetooth;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.Bluetooth;
import app.nzyme.core.bluetooth.db.BluetoothDeviceSummary;
import app.nzyme.core.bluetooth.sig.BluetoothDeviceClass;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.database.OrderDirection;
import app.nzyme.core.database.generic.StringNumberAggregationResult;
import app.nzyme.core.database.generic.TwoColumnHistogramOrderColumn;
import app.nzyme.core.rest.RestTools;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.responses.bluetooth.*;
import app.nzyme.core.rest.responses.metrics.HistogramResponse;
import app.nzyme.core.rest.responses.shared.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
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

@Path("/api/bluetooth/devices")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
@Tag(name = "Bluetooth", description = "Bluetooth devices that the Bluetooth adapters of your taps discovered in "
        + "range, over Bluetooth Classic, Bluetooth Low Energy or both. The data includes device names, "
        + "manufacturers, device classes, discovered services and signal strength.")
public class BluetoothDevicesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Operation(operationId = "findBluetoothDevices", summary = "List Bluetooth devices",
            description = "Returns all Bluetooth devices seen in the time range, strongest average signal first by "
                    + "default. Devices are grouped by MAC address, so names, aliases and transports of a device "
                    + "arrive as lists.")
    @ApiResponse(responseCode = "200", description = "Devices found.",
            content = @Content(schema = @Schema(implementation = BluetoothDeviceSummaryListResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization or tenant not found, or not accessible by the calling user.", content = @Content)
    public Response findAll(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                            @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                            @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                            @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                            @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                            @Parameter(description = "Page offset.") @QueryParam("offset") int offset,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Path("/histogram")
    @Operation(operationId = "findBluetoothDeviceCountHistogram", summary = "Get histogram of Bluetooth device counts",
            description = "Returns the number of Bluetooth devices seen in each bucket of the time range. The bucket "
                    + "size is chosen automatically based on the length of the time range.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = NumericHistogramResponse.class)))
    public Response deviceCountHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                         @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                         @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                         @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        Map<DateTime, Integer> buckets = Maps.newHashMap();
        for (GenericIntegerHistogramEntry bucket : nzyme.getBluetooth()
                .getDeviceCountHistogram(timeRange, bucketing, filters, tapUUIDs)) {
            buckets.put(bucket.bucket(), bucket.value());
        }

        return Response.ok(NumericHistogramResponse.create(buckets, bucketing.bucketSizeMs())).build();
    }

    @GET
    @Path("/manufacturers/histogram")
    @Operation(operationId = "findBluetoothManufacturers", summary = "List Bluetooth device manufacturers",
            description = "Returns the manufacturers of the Bluetooth devices seen in the time range, together with "
                    + "the number of devices per manufacturer. The manufacturer comes from the Bluetooth company "
                    + "identifier the device advertised, so devices without a known manufacturer are left out.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    public Response manufacturersHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                           @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                           @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                           @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                           @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                           @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                           @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                           @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getBluetooth().getDeviceManufacturerHistogramCount(timeRange, filters, tapUUIDs);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberAggregationResult x : nzyme.getBluetooth()
                .getDeviceManufacturerHistogram(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(x.key(), HistogramValueType.GENERIC, null),
                    HistogramValueStructureResponse.create(x.value(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/ouis/histogram")
    @Operation(operationId = "findBluetoothOuis", summary = "List Bluetooth device OUIs",
            description = "Returns the OUIs of the Bluetooth devices seen in the time range, together with the number "
                    + "of devices per OUI. The OUI is resolved from the vendor part of the device MAC address, so "
                    + "devices with a randomized address do not appear here.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = TwoColumnTableHistogramResponse.class)))
    @ApiResponse(responseCode = "400", description = "The sorting column or direction is not valid.", content = @Content)
    public Response ouisHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @Parameter(description = "JSON encoded filter definition as produced by the web interface filter builder.") @QueryParam("filters") String filtersParameter,
                                  @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps,
                                  @Parameter(description = "Sorting column. Must be passed together with the sorting direction.") @QueryParam("order_column") @Nullable String orderColumnParam,
                                  @Parameter(description = "Sorting direction, ASC or DESC.") @QueryParam("order_direction") @Nullable String orderDirectionParam,
                                  @Parameter(description = "Page size.") @QueryParam("limit") int limit,
                                  @Parameter(description = "Page offset.") @QueryParam("offset") int offset) {
        List<UUID> tapUUIDs = parseAndValidateTapIds(getAuthenticatedUser(sc), nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Filters filters = parseFiltersQueryParameter(filtersParameter);

        TwoColumnHistogramOrderColumn orderColumn = TwoColumnHistogramOrderColumn.VALUE;
        OrderDirection orderDirection = OrderDirection.DESC;
        if (orderColumnParam != null && orderDirectionParam != null) {
            try {
                orderColumn = TwoColumnHistogramOrderColumn.valueOf(orderColumnParam.toUpperCase());
                orderDirection = OrderDirection.valueOf(orderDirectionParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }

        long count = nzyme.getBluetooth().getDeviceOUIHistogramCount(timeRange, filters, tapUUIDs);

        List<TwoColumnTableHistogramValueResponse> values = Lists.newArrayList();
        for (StringNumberAggregationResult x : nzyme.getBluetooth()
                .getDeviceOUIHistogram(timeRange, filters, limit, offset, orderColumn, orderDirection, tapUUIDs)) {
            values.add(TwoColumnTableHistogramValueResponse.create(
                    HistogramValueStructureResponse.create(x.key(), HistogramValueType.GENERIC, null),
                    HistogramValueStructureResponse.create(x.value(), HistogramValueType.INTEGER, null)
            ));
        }

        return Response.ok(TwoColumnTableHistogramResponse.create(count, true, values)).build();
    }

    @GET
    @Path("/show/{mac}")
    @Operation(operationId = "findBluetoothDevice", summary = "Get a Bluetooth device",
            description = "Returns everything Nzyme knows about one Bluetooth device, including the context of its "
                    + "MAC address. Devices are grouped by MAC address, so names, aliases, transports and "
                    + "discovered services arrive as lists.")
    @ApiResponse(responseCode = "200", description = "Device found.",
            content = @Content(schema = @Schema(implementation = BluetoothDeviceDetailsResponse.class)))
    @ApiResponse(responseCode = "404", description = "Device not found, or organization or tenant not accessible by the calling user.", content = @Content)
    public Response findOne(@Parameter(hidden = true) @Context SecurityContext sc,
                            @Parameter(description = "Organization UUID.") @QueryParam("organization_id") UUID organizationId,
                            @Parameter(description = "Tenant UUID.") @QueryParam("tenant_id") UUID tenantId,
                            @Parameter(description = "MAC address of the Bluetooth device.") @PathParam("mac") @MacAddress String mac,
                            @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findBluetoothDeviceRssiHistogram", summary = "Get signal strength histogram of a Bluetooth device",
            description = "Returns the average signal strength of the device in each bucket of the time range. The "
                    + "response is an object that maps the bucket timestamp to the signal strength in dBm. Buckets "
                    + "in which no tap saw the device are missing from the response.")
    @ApiResponse(responseCode = "200", description = "Histogram found.",
            content = @Content(schema = @Schema(implementation = Object.class)))
    public Response rssiHistogram(@Parameter(hidden = true) @Context SecurityContext sc,
                                  @Parameter(description = "MAC address of the Bluetooth device.") @PathParam("mac") @MacAddress String mac,
                                  @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
    @Operation(operationId = "findBluetoothDeviceRssiByTap", summary = "Get signal strength of a Bluetooth device per tap",
            description = "Returns the average signal strength of the device in the time range, one entry per tap "
                    + "that saw it. Taps that did not see the device are left out, so the list can be empty.")
    @ApiResponse(responseCode = "200", description = "Signal strengths found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TapBasedSignalStrengthResponse.class))))
    public Response rssiByTap(@Parameter(hidden = true) @Context SecurityContext sc,
                              @Parameter(description = "MAC address of the Bluetooth device.") @PathParam("mac") @MacAddress String mac,
                              @Parameter(description = "Time range selector. Accepts the same format as the web interface time range picker.") @QueryParam("time_range") @Valid String timeRangeParameter,
                              @Parameter(description = "Comma separated list of tap UUIDs to include. Omit for all taps the user can access.") @QueryParam("taps") String taps) {
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
                                                macAddressContextEntry.description(),
                                                macAddressContextEntry.notes()
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
