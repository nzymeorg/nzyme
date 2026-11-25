package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

@AutoValue
public abstract class SecuritySuitesResponse {

    @JsonProperty("pairwise_ciphers")
    @Nullable
    public abstract String pairwiseCiphers();

    @JsonProperty("group_cipher")
    @Nullable
    public abstract String groupCipher();

    @JsonProperty("key_management_modes")
    @Nullable
    public abstract String keyManagementModes();

    @JsonProperty("pmf_mode")
    @Nullable
    public abstract String pmfMode();

    @JsonProperty("operating_channel_validation")
    @Nullable
    public abstract Boolean operatingChannelValidation();

    @JsonProperty("beacon_protection")
    @Nullable
    public abstract Boolean beaconProtection();

    @JsonProperty("identifier")
    @Nullable
    public abstract String identifier();

    public static SecuritySuitesResponse create(String pairwiseCiphers, String groupCipher, String keyManagementModes, String pmfMode, Boolean operatingChannelValidation, Boolean beaconProtection, String identifier) {
        return builder()
                .pairwiseCiphers(pairwiseCiphers)
                .groupCipher(groupCipher)
                .keyManagementModes(keyManagementModes)
                .pmfMode(pmfMode)
                .operatingChannelValidation(operatingChannelValidation)
                .beaconProtection(beaconProtection)
                .identifier(identifier)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SecuritySuitesResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder pairwiseCiphers(String pairwiseCiphers);

        public abstract Builder groupCipher(String groupCipher);

        public abstract Builder keyManagementModes(String keyManagementModes);

        public abstract Builder pmfMode(String pmfMode);

        public abstract Builder operatingChannelValidation(Boolean operatingChannelValidation);

        public abstract Builder beaconProtection(Boolean beaconProtection);

        public abstract Builder identifier(String identifier);

        public abstract SecuritySuitesResponse build();
    }
}
