package app.nzyme.core.dot11.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

@AutoValue
public abstract class Dot11SecuritySuiteJson {

    @Nullable
    public abstract String pairwiseCiphers();

    @Nullable
    public abstract String groupCipher();

    @Nullable
    public abstract String keyManagementModes();

    @Nullable
    public abstract String pmfMode();

    // Empty for networks recorded before taps reported it.
    @Nullable
    public abstract Boolean operatingChannelValidation();

    @Nullable
    public abstract Boolean beaconProtection();

    @JsonCreator
    public static Dot11SecuritySuiteJson create(@JsonProperty("pairwise_ciphers") String pairwiseCiphers,
                                                @JsonProperty("group_cipher") String groupCipher,
                                                @JsonProperty("key_management_modes") String keyManagementModes,
                                                @JsonProperty("pmf_mode") String pmfMode,
                                                @JsonProperty("operating_channel_validation") @Nullable Boolean operatingChannelValidation,
                                                @JsonProperty("beacon_protection") @Nullable Boolean beaconProtection) {
        return builder()
                .pairwiseCiphers(pairwiseCiphers)
                .groupCipher(groupCipher)
                .keyManagementModes(keyManagementModes)
                .pmfMode(pmfMode)
                .operatingChannelValidation(operatingChannelValidation)
                .beaconProtection(beaconProtection)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Dot11SecuritySuiteJson.Builder();
    }

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder pairwiseCiphers(String pairwiseCiphers);

        public abstract Builder groupCipher(String groupCipher);

        public abstract Builder keyManagementModes(String keyManagementModes);

        public abstract Builder pmfMode(String pmfMode);

        public abstract Builder operatingChannelValidation(Boolean operatingChannelValidation);

        public abstract Builder beaconProtection(Boolean beaconProtection);

        public abstract Dot11SecuritySuiteJson build();
    }
}
