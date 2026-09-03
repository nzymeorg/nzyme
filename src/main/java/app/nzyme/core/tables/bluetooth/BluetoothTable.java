package app.nzyme.core.tables.bluetooth;

import app.nzyme.core.bluetooth.db.BluetoothServiceUuidJson;
import app.nzyme.core.rest.resources.taps.reports.tables.bluetooth.BluetoothDeviceReport;
import app.nzyme.core.rest.resources.taps.reports.tables.bluetooth.BluetoothDevicesReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BluetoothTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(BluetoothTable.class);

    private final TablesService tablesService;

    private final Timer totalReportTimer;
    private final ObjectMapper om;

    public BluetoothTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.BLUETOOTH_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, BluetoothDevicesReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeDevices(handle, tapUuid, report.devices());
            }
        });
    }

    private void writeDevices(Handle handle, UUID tapUuid, List<BluetoothDeviceReport> devices) {
        PreparedBatch batch = handle.prepareBatch("INSERT INTO bluetooth_devices(uuid, tap_uuid, mac, oui, " +
                "alias, device, transport, name, rssi, company_id, class_number, appearance, modalias, tx_power, " +
                "manufacturer_data, manufacturer_name, uuids, service_data, tags, last_seen, created_at) " +
                "VALUES(:uuid, :tap_uuid, :mac, :oui, :alias, :device, :transport, :name, :rssi, :company_id, " +
                ":class_number, :appearance, :modalias, :tx_power, :manufacturer_data, :manufacturer_name, " +
                ":uuids, :service_data, :tags::jsonb, :last_seen, NOW())");

        for (BluetoothDeviceReport device : devices) {
            if (device.rssi() == null || device.rssi() == 0) {
                /*
                 * Sometimes devices are reported as a 0 RSSI. Those are usually currently paired devices.
                 */
                continue;
            }

            // OUI.
            Optional<String> oui = tablesService.getNzyme()
                    .getOuiService()
                    .lookup(device.mac());

            // Manufacturer name.
            Optional<String> manufacturerName;
            if (device.companyId() != null) {
                manufacturerName = tablesService.getNzyme()
                        .getBluetoothSigService()
                        .lookupCompanyId(device.companyId());
            } else {
                manufacturerName = Optional.empty();
            }

            List<BluetoothServiceUuidJson> serviceUuids = Lists.newArrayList();
            if (device.uuids() != null) {
                for (String uuid : device.uuids()) {
                    try {
                        serviceUuids.add(BluetoothServiceUuidJson.create(
                                uuid,
                                tablesService.getNzyme().getBluetoothSigService()
                                        .lookupServiceUuid(extract16BitUuid(uuid))
                                        .orElse(null)
                        ));
                    } catch(InvalidBluetoothUuidException e) {
                        LOG.debug("Could not build Bluetooth Service UUID from UUID [{}] for MAC [{}]. " +
                                "Skipping.", uuid, device.mac(), e);
                    }
                }
            }

            String uuids = null;
            String serviceData = null;
            try {
                // Service UUIDs.
                if (!serviceUuids.isEmpty()) {
                    uuids = om.writeValueAsString(serviceUuids);
                }
                serviceData = om.writeValueAsString(device.serviceData());
            } catch (JacksonException e) {
                LOG.warn("Could not serialize Bluetooth device data. Skipping attributes.", e);
            }

            String tags;
            if (device.tags() != null) {
                try {
                    tags = om.writeValueAsString(device.tags());
                } catch (JacksonException e) {
                    LOG.error("Could not write reported tags of Bluetooth device [{}] to JSON. Skipping tags.",
                            device.mac(), e);
                    tags = null;
                }
            } else {
                tags = null;
            }

            batch
                    .bind("uuid", UUID.randomUUID())
                    .bind("tap_uuid", tapUuid)
                    .bind("mac", device.mac())
                    .bind("oui", oui)
                    .bind("alias", device.alias())
                    .bind("device", device.device())
                    .bind("transport", device.transport())
                    .bind("name", device.name())
                    .bind("rssi", device.rssi())
                    .bind("company_id", device.companyId())
                    .bind("class_number", device.classNumber())
                    .bind("appearance", device.appearance())
                    .bind("modalias", device.modalias())
                    .bind("tx_power", device.txPower())
                    .bind("manufacturer_data", device.manufacturerData())
                    .bind("manufacturer_name", manufacturerName)
                    .bind("uuids", uuids)
                    .bind("service_data", serviceData)
                    .bind("tags", tags)
                    .bind("last_seen", device.lastSeen())
                    .add();
        }

        batch.execute();
    }

    private static String extract16BitUuid(String uuidStr) throws InvalidBluetoothUuidException {
        if (uuidStr == null || uuidStr.isEmpty()) {
            throw new InvalidBluetoothUuidException("UUID is null or empty");
        }

        uuidStr = uuidStr.toUpperCase();

        // Confirm it's a valid UUID
        try {
            UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidBluetoothUuidException("Not a valid UUID");
        }

        // Extract the first 4 characters from the UUID string (the 16-bit UUID part)
        return uuidStr.substring(0, 8).replace("0000", "0x");
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

    public static final class InvalidBluetoothUuidException extends Exception {
        public InvalidBluetoothUuidException(String msg) {
            super(msg);
        }
    }

}
