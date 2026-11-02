package app.nzyme.core.rest.responses.ethernet.dhcp;

import app.nzyme.core.rest.responses.ethernet.EthernetMacAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.UUID;

@AutoValue
public abstract class DHCPFingerprintExclusionMatchedAssetResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("mac")
    public abstract EthernetMacAddressResponse mac();

    public static DHCPFingerprintExclusionMatchedAssetResponse create(UUID uuid, EthernetMacAddressResponse mac) {
        return builder()
                .uuid(uuid)
                .mac(mac)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DHCPFingerprintExclusionMatchedAssetResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder mac(EthernetMacAddressResponse mac);

        public abstract DHCPFingerprintExclusionMatchedAssetResponse build();
    }
}
