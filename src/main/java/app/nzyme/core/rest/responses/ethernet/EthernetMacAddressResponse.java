package app.nzyme.core.rest.responses.ethernet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.UUID;

@AutoValue
public abstract class EthernetMacAddressResponse {

    @JsonProperty("address")
    public abstract String address();

    @JsonProperty("oui")
    @Nullable
    public abstract String oui();

    @JsonProperty("asset_id")
    @Nullable
    public abstract UUID assetId();

    @JsonProperty("asset_is_active")
    @Nullable
    public abstract Boolean assetIsActive();

    @JsonProperty("context")
    @Nullable
    public abstract EthernetMacAddressContextResponse context();

    @Schema(description = "What the MAC address is to the IP address it is returned with. DEVICE: its asset claimed "
            + "the private IPv4 address by ARP, it is the device that has the address. UNCLAIMED: it did not claim "
            + "the private IPv4 address, usually because it is a router that forwarded the traffic. PUBLIC: the "
            + "address is a public one, which usually is behind the gateway. UNKNOWN for every other address. "
            + "For UNCLAIMED and PUBLIC, the MAC address comes without asset and context. Not set where the MAC "
            + "address is returned on its own.")
    @JsonProperty("role")
    @Nullable
    public abstract L4AddressMacRoleResponse role();

    public static EthernetMacAddressResponse create(String address, String oui, UUID assetId, Boolean assetIsActive, EthernetMacAddressContextResponse context) {
        return builder()
                .address(address)
                .oui(oui)
                .assetId(assetId)
                .assetIsActive(assetIsActive)
                .context(context)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_EthernetMacAddressResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder address(String address);

        public abstract Builder oui(String oui);

        public abstract Builder assetId(UUID assetId);

        public abstract Builder assetIsActive(Boolean assetIsActive);

        public abstract Builder context(EthernetMacAddressContextResponse context);

        public abstract Builder role(L4AddressMacRoleResponse role);

        public abstract EthernetMacAddressResponse build();
    }
}
