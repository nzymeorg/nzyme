package app.nzyme.core.database;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.Dot11RegistryKeys;
import app.nzyme.core.ethernet.EthernetRegistryKeys;
import app.nzyme.core.rest.responses.bluetooth.BluetoothRegistryKeys;
import app.nzyme.core.timelines.TimelinesRegistryKeys;
import app.nzyme.core.uav.UavRegistryKeys;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class DatabaseTools {

    public static int getDataCategoryRetentionTimeDays(NzymeNode nzyme,
                                                       DataCategory category,
                                                       @NotNull UUID organizationId,
                                                       @NotNull UUID tenantId) {
        String key = null;
        String defaultValue = null;
        switch (category) {
            case DOT11 -> {
                key = Dot11RegistryKeys.DOT11_RETENTION_TIME_DAYS.key();
                defaultValue = Dot11RegistryKeys.DOT11_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case BLUETOOTH -> {
                key = BluetoothRegistryKeys.BLUETOOTH_RETENTION_TIME_DAYS.key();
                defaultValue = BluetoothRegistryKeys.BLUETOOTH_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ETHERNET_L4 -> {
                key = EthernetRegistryKeys.L4_RETENTION_TIME_DAYS.key();
                defaultValue = EthernetRegistryKeys.L4_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ETHERNET_DNS -> {
                key = EthernetRegistryKeys.DNS_RETENTION_TIME_DAYS.key();
                defaultValue = EthernetRegistryKeys.DNS_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case PORTAL_INTEGRITY -> {
                    key = EthernetRegistryKeys.PORTAL_INTEGRITY_RETENTION_TIME_DAYS.key();
                    defaultValue = EthernetRegistryKeys.PORTAL_INTEGRITY_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case UAV -> {
                key = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.key();
                defaultValue = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case TIMELINE_EVENTS_DOT11 -> {
                key = TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.key();
                defaultValue = TimelinesRegistryKeys.DOT11_EVENTS_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
        }

        return Integer.parseInt(nzyme.getDatabaseCoreRegistry()
                .getValue(key, organizationId, tenantId)
                .orElse(defaultValue));
    }

}
