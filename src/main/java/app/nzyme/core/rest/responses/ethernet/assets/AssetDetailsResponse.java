package app.nzyme.core.rest.responses.ethernet.assets;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class AssetDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract EthernetMacAddressResponse mac();

    @Nullable
    @JsonProperty("oui")
    public abstract String oui();

    @JsonProperty("is_active")
    public abstract boolean isActive();

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("dhcp_fingerprint_initial")
    public abstract String dhcpFingerprintInitial();

    @Nullable
    @JsonProperty("dhcp_fingerprint_renew")
    public abstract String dhcpFingerprintRenew();

    @Nullable
    @JsonProperty("dhcp_fingerprint_reboot")
    public abstract String dhcpFingerprintReboot();

    @Nullable
    @JsonProperty("dhcp_fingerprint_rebind")
    public abstract String dhcpFingerprintRebind();

    @JsonProperty("seen_arp")
    public abstract boolean seenArp();

    @JsonProperty("seen_ndp")
    public abstract boolean seenNdp();

    @JsonProperty("seen_dhcp")
    public abstract boolean seenDhcp();

    @JsonProperty("seen_tcp")
    public abstract boolean seenTcp();

    @JsonProperty("seen_udp")
    public abstract boolean seenUdp();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetDetailsResponse create(UUID uuid, EthernetMacAddressResponse mac, String oui, boolean isActive, String name, String dhcpFingerprintInitial, String dhcpFingerprintRenew, String dhcpFingerprintReboot, String dhcpFingerprintRebind, boolean seenArp, boolean seenNdp, boolean seenDhcp, boolean seenTcp, boolean seenUdp, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .oui(oui)
                .isActive(isActive)
                .name(name)
                .dhcpFingerprintInitial(dhcpFingerprintInitial)
                .dhcpFingerprintRenew(dhcpFingerprintRenew)
                .dhcpFingerprintReboot(dhcpFingerprintReboot)
                .dhcpFingerprintRebind(dhcpFingerprintRebind)
                .seenArp(seenArp)
                .seenNdp(seenNdp)
                .seenDhcp(seenDhcp)
                .seenTcp(seenTcp)
                .seenUdp(seenUdp)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder mac(EthernetMacAddressResponse mac);

        public abstract Builder oui(String oui);

        public abstract Builder isActive(boolean isActive);

        public abstract Builder name(String name);

        public abstract Builder dhcpFingerprintInitial(String dhcpFingerprintInitial);

        public abstract Builder dhcpFingerprintRenew(String dhcpFingerprintRenew);

        public abstract Builder dhcpFingerprintReboot(String dhcpFingerprintReboot);

        public abstract Builder dhcpFingerprintRebind(String dhcpFingerprintRebind);

        public abstract Builder seenArp(boolean seenArp);
        public abstract Builder seenNdp(boolean seenNdp);

        public abstract Builder seenDhcp(boolean seenDhcp);

        public abstract Builder seenTcp(boolean seenTcp);

        public abstract Builder seenUdp(boolean seenUdp);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetDetailsResponse build();
    }
}