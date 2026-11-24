package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Body for accepting a DHCP fingerprint as the known fingerprint of an asset.")
@AutoValue
public abstract class AcceptAssetDHCPFingerprintRequest {

    @NotEmpty
    public abstract String fingerprint();

    @JsonCreator
    public static AcceptAssetDHCPFingerprintRequest create(@Schema(description = "DHCP fingerprint to accept, 64 hexadecimal characters.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("fingerprint") @NotEmpty String fingerprint) {
        return builder()
                .fingerprint(fingerprint)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AcceptAssetDHCPFingerprintRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder fingerprint(@NotEmpty String fingerprint);

        public abstract AcceptAssetDHCPFingerprintRequest build();
    }
}
