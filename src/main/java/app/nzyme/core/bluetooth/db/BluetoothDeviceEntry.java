package app.nzyme.core.bluetooth.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class BluetoothDeviceEntry {

    public abstract UUID uuid();
    public abstract UUID tapUuid();
    public abstract String mac();
    @Nullable
    public abstract String oui();
    public abstract String alias();
    public abstract String device();
    public abstract String transport();
    @Nullable
    public abstract String name();
    @Nullable
    public abstract Integer rssi();
    @Nullable
    public abstract Integer companyId();
    @Nullable
    public abstract Integer classNumber();
    @Nullable
    public abstract Integer appearance();
    @Nullable
    public abstract String modAlias();
    @Nullable
    public abstract Integer txPower();
    @Nullable
    public abstract String manufacturerData();
    @Nullable
    public abstract String manufacturerName();
    @Nullable
    public abstract String uuids();
    @Nullable
    public abstract String serviceData();
    public abstract DateTime lastSeen();
    public abstract DateTime createdAt();

    public static BluetoothDeviceEntry create(UUID uuid, UUID tapUuid, String mac, String oui, String alias, String device, String transport, String name, Integer rssi, Integer companyId, Integer classNumber, Integer appearance, String modAlias, Integer txPower, String manufacturerData, String manufacturerName, String uuids, String serviceData, DateTime lastSeen, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .tapUuid(tapUuid)
                .mac(mac)
                .oui(oui)
                .alias(alias)
                .device(device)
                .transport(transport)
                .name(name)
                .rssi(rssi)
                .companyId(companyId)
                .classNumber(classNumber)
                .appearance(appearance)
                .modAlias(modAlias)
                .txPower(txPower)
                .manufacturerData(manufacturerData)
                .manufacturerName(manufacturerName)
                .uuids(uuids)
                .serviceData(serviceData)
                .lastSeen(lastSeen)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BluetoothDeviceEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder mac(String mac);

        public abstract Builder oui(String oui);

        public abstract Builder alias(String alias);

        public abstract Builder device(String device);

        public abstract Builder transport(String transport);

        public abstract Builder name(String name);

        public abstract Builder rssi(Integer rssi);

        public abstract Builder companyId(Integer companyId);

        public abstract Builder classNumber(Integer classNumber);

        public abstract Builder appearance(Integer appearance);

        public abstract Builder modAlias(String modAlias);

        public abstract Builder txPower(Integer txPower);

        public abstract Builder manufacturerData(String manufacturerData);

        public abstract Builder manufacturerName(String manufacturerName);

        public abstract Builder uuids(String uuids);

        public abstract Builder serviceData(String serviceData);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract BluetoothDeviceEntry build();
    }
}
