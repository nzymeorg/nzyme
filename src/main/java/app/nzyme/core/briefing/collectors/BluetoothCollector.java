package app.nzyme.core.briefing.collectors;

import app.nzyme.core.briefing.BriefingContext;
import app.nzyme.core.briefing.BriefingItems;
import app.nzyme.core.briefing.BriefingLinks;
import app.nzyme.core.rest.responses.briefing.*;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

import static app.nzyme.core.briefing.BriefingItems.plural;

public class BluetoothCollector {

    private static final int MAX_SAMPLES = 6;

    private final BriefingContext ctx;

    private long monitorsTotal;
    private long monitorsEnabled;
    private long devicesCurrent;
    private long devicesPrevious;
    private long devicesNew;
    private List<BriefingSampleResponse> newDeviceSamples;

    public BluetoothCollector(BriefingContext ctx) {
        this.ctx = ctx;
    }

    public BluetoothCollector load() {
        ctx.nzyme().getDatabase().useHandle(handle -> {
            monitorsTotal = handle.createQuery("SELECT COUNT(*) FROM monitors WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND type LIKE 'BLUETOOTH_%'")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
            monitorsEnabled = handle.createQuery("SELECT COUNT(*) FROM monitors WHERE organization_id = :organization_id " +
                            "AND tenant_id = :tenant_id AND type LIKE 'BLUETOOTH_%' AND enabled = true")
                    .bind("organization_id", ctx.organizationId())
                    .bind("tenant_id", ctx.tenantId())
                    .mapTo(Long.class).one();
        });

        Filters none = Filters.create(Maps.newHashMap());
        devicesCurrent = ctx.nzyme().getBluetooth().countAllDevices(ctx.window().current(), none, ctx.taps());
        devicesPrevious = ctx.nzyme().getBluetooth().countAllDevices(ctx.window().previous(), none, ctx.taps());

        loadNewDevices();

        return this;
    }

    // Devices whose earliest retained sighting falls into the window. Older sightings may have been
    // deleted by retention, so this is "first seen in the data we still have".
    private void loadNewDevices() {
        devicesNew = 0;
        newDeviceSamples = Lists.newArrayList();

        if (ctx.taps().isEmpty()) {
            return;
        }

        ctx.nzyme().getDatabase().useHandle(handle -> {
            devicesNew = handle.createQuery("SELECT COUNT(*) FROM (SELECT mac FROM bluetooth_devices " +
                            "WHERE tap_uuid IN (<taps>) GROUP BY mac " +
                            "HAVING MIN(last_seen) >= :from AND MIN(last_seen) <= :to) AS new_devices")
                    .bindList("taps", ctx.taps())
                    .bind("from", ctx.window().from())
                    .bind("to", ctx.window().to())
                    .mapTo(Long.class)
                    .one();

            handle.createQuery("SELECT mac, MIN(last_seen) AS first_seen, MAX(name) AS name, " +
                            "MAX(manufacturer_name) AS manufacturer FROM bluetooth_devices " +
                            "WHERE tap_uuid IN (<taps>) GROUP BY mac " +
                            "HAVING MIN(last_seen) >= :from AND MIN(last_seen) <= :to " +
                            "ORDER BY first_seen DESC LIMIT :limit")
                    .bindList("taps", ctx.taps())
                    .bind("from", ctx.window().from())
                    .bind("to", ctx.window().to())
                    .bind("limit", MAX_SAMPLES)
                    .map((rs, c) -> {
                        String mac = rs.getString("mac");
                        String name = rs.getString("name");
                        String manufacturer = rs.getString("manufacturer");
                        String detail = name != null && !name.isBlank() ? name : manufacturer;
                        return BriefingItems.sample(mac, detail, new DateTime(rs.getTimestamp("first_seen")), null,
                                BriefingLinks.to("BLUETOOTH_DEVICE", "mac", mac));
                    })
                    .forEach(newDeviceSamples::add);
        });
    }

    public boolean inUse(TapsCollector taps) {
        return taps.capturesType("Bluetooth") || devicesCurrent + devicesPrevious > 0;
    }

    public List<BriefingCheckResponse> checks() {
        List<BriefingCheckResponse> checks = Lists.newArrayList();

        BriefingLinkResponse monitors = ctx.canManageBluetoothMonitoring() ? BriefingLinks.to("BLUETOOTH_MONITORS") : null;

        if (monitorsTotal == 0) {
            checks.add(BriefingItems.info("bluetooth_monitors", "No Bluetooth monitors configured",
                    "Monitors alert on custom conditions, for example a device showing up or leaving.", monitors));
        } else {
            checks.add(BriefingItems.check("bluetooth_monitors", monitorsEnabled == monitorsTotal ? "OK" : "INFO",
                    monitorsEnabled + " of " + monitorsTotal + " Bluetooth " + plural(monitorsTotal, "monitor", "monitors") + " enabled",
                    null, monitors));
        }

        return checks;
    }

    public BriefingChangeGroupResponse devicesGroup() {
        BriefingLinkResponse devices = BriefingLinks.to("BLUETOOTH_DEVICES");

        List<BriefingChangeTileResponse> tiles = Lists.newArrayList();
        tiles.add(BriefingItems.tile("bluetooth_devices_seen", "Devices seen", devicesCurrent, devicesPrevious,
                null, null, devices, List.of()));
        tiles.add(BriefingItems.tile("bluetooth_devices_new", "Devices seen for the first time", devicesNew, null,
                null, null, devices, newDeviceSamples));

        return BriefingItems.group("bluetooth_devices", "Devices", "BLUETOOTH", devices, tiles,
                "Distinct Bluetooth devices your taps saw, compared to the window before, and devices that showed up " +
                        "for the first time. Devices seen earlier than the data retention period count as new again.");
    }

    public Map<String, Object> activity() {
        Map<String, Object> activity = Maps.newHashMap();

        TimeRange combined = ctx.window().combined();
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(combined);

        List<BriefingHistogramBucketResponse> devices = Lists.newArrayList();
        for (GenericIntegerHistogramEntry e : ctx.nzyme().getBluetooth().getDeviceCountHistogram(
                combined, bucketing, Filters.create(Maps.newHashMap()), ctx.taps())) {
            devices.add(BriefingHistogramBucketResponse.create(e.bucket(), e.value()));
        }
        activity.put("devices", devices);

        return activity;
    }

}
