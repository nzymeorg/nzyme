package app.nzyme.core.assets.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract String mac();
    public abstract boolean isActive();
    @Nullable
    public abstract String dhcpFingerprintInitial();
    @Nullable
    public abstract String dhcpFingerprintRenew();
    @Nullable
    public abstract String dhcpFingerprintReboot();
    @Nullable
    public abstract String dhcpFingerprintRebind();
    public abstract boolean seenArp();
    public abstract boolean seenDhcp();
    public abstract boolean seenTcp();
    public abstract boolean seenUdp();
    public abstract List<String> hostnames();
    public abstract List<String> ipAddresses();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();
    public abstract DateTime updatedAt();
    public abstract DateTime createdAt();

    public static AssetEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, String mac, boolean isActive, String dhcpFingerprintInitial, String dhcpFingerprintRenew, String dhcpFingerprintReboot, String dhcpFingerprintRebind, boolean seenArp, boolean seenDhcp, boolean seenTcp, boolean seenUdp, List<String> hostnames, List<String> ipAddresses, DateTime firstSeen, DateTime lastSeen, DateTime updatedAt, DateTime createdAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .mac(mac)
                .isActive(isActive)
                .dhcpFingerprintInitial(dhcpFingerprintInitial)
                .dhcpFingerprintRenew(dhcpFingerprintRenew)
                .dhcpFingerprintReboot(dhcpFingerprintReboot)
                .dhcpFingerprintRebind(dhcpFingerprintRebind)
                .seenArp(seenArp)
                .seenDhcp(seenDhcp)
                .seenTcp(seenTcp)
                .seenUdp(seenUdp)
                .hostnames(hostnames)
                .ipAddresses(ipAddresses)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .updatedAt(updatedAt)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder mac(String mac);

        public abstract Builder isActive(boolean isActive);

        public abstract Builder dhcpFingerprintInitial(String dhcpFingerprintInitial);

        public abstract Builder dhcpFingerprintRenew(String dhcpFingerprintRenew);

        public abstract Builder dhcpFingerprintReboot(String dhcpFingerprintReboot);

        public abstract Builder dhcpFingerprintRebind(String dhcpFingerprintRebind);

        public abstract Builder seenArp(boolean seenArp);

        public abstract Builder seenDhcp(boolean seenDhcp);

        public abstract Builder seenTcp(boolean seenTcp);

        public abstract Builder seenUdp(boolean seenUdp);

        public abstract Builder hostnames(List<String> hostnames);

        public abstract Builder ipAddresses(List<String> ipAddresses);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract AssetEntry build();
    }
}
