package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(description = "Body for adding values to the expected portal of a control URL. Every field is optional, but at least one must be set.")
@AutoValue
public abstract class PortalIntegrityExpectedValuesRequest {

    @Nullable
    public abstract String finalUrl();

    @Nullable
    public abstract String address();

    @Nullable
    public abstract String certificate();

    @JsonCreator
    public static PortalIntegrityExpectedValuesRequest create(@Schema(description = "Final URL to add. The query string is ignored.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("final_url") String finalUrl,
            @Schema(description = "Address to add.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("address") String address,
            @Schema(description = "SHA-256 certificate fingerprint to add.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("certificate") String certificate) {
        return builder()
                .finalUrl(finalUrl)
                .address(address)
                .certificate(certificate)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityExpectedValuesRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder finalUrl(String finalUrl);

        public abstract Builder address(String address);

        public abstract Builder certificate(String certificate);

        public abstract PortalIntegrityExpectedValuesRequest build();
    }
}
