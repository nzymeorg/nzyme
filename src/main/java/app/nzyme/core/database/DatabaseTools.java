package app.nzyme.core.database;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.assets.AssetRegistryKeys;
import app.nzyme.core.assets.identity.AssetIdentityRegistryKeys;
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
            case TLS_CERTIFICATES -> {
                key = EthernetRegistryKeys.TLS_CERTIFICATES_RETENTION_TIME_DAYS.key();
                defaultValue = EthernetRegistryKeys.TLS_CERTIFICATES_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case UAV -> {
                key = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.key();
                defaultValue = UavRegistryKeys.UAV_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ASSET_ACTIVITY -> {
                key = AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS.key();
                defaultValue = AssetRegistryKeys.ASSETS_ACTIVITY_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ASSET_ATTRIBUTES -> {
                key = AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS.key();
                defaultValue = AssetRegistryKeys.ASSETS_ATTRIBUTES_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ASSET_COMMUNICATION -> {
                key = AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS.key();
                defaultValue = AssetRegistryKeys.ASSETS_COMMUNICATION_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ASSET_STATISTICS -> {
                key = AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.key();
                defaultValue = AssetRegistryKeys.ASSETS_STATISTICS_RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
            }
            case ASSET_IDENTITY -> {
                key = AssetIdentityRegistryKeys.RETENTION_TIME_DAYS.key();
                defaultValue = AssetIdentityRegistryKeys.RETENTION_TIME_DAYS.defaultValue().orElse("MISSING");
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
