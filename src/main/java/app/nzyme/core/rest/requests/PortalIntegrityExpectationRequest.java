package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;

@Schema(description = "Body for setting what a probe is expected to see when it requests a control URL.")
@AutoValue
public abstract class PortalIntegrityExpectationRequest {

    @Nullable
    public abstract String expectation();

    @Nullable
    public abstract List<String> expectedFinalUrls();

    @Nullable
    public abstract List<String> expectedAddresses();

    @Nullable
    public abstract List<String> expectedCertificates();

    @JsonCreator
    public static PortalIntegrityExpectationRequest create(@Schema(description = "NOT_MONITORED, PORTAL or NO_PORTAL.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("expectation") String expectation,
            @Schema(description = "Final URLs of the expected portal. The query string is ignored. At least one is required to expect a portal.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("expected_final_urls") List<String> expectedFinalUrls,
            @Schema(description = "Addresses the host of the expected portal may resolve to. Leave empty to not check the address.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("expected_addresses") List<String> expectedAddresses,
            @Schema(description = "SHA-256 fingerprints of the certificates the expected portal may present. Leave empty to not check the certificate.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("expected_certificates") List<String> expectedCertificates) {
        return builder()
                .expectation(expectation)
                .expectedFinalUrls(expectedFinalUrls)
                .expectedAddresses(expectedAddresses)
                .expectedCertificates(expectedCertificates)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_PortalIntegrityExpectationRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder expectation(String expectation);

        public abstract Builder expectedFinalUrls(List<String> expectedFinalUrls);

        public abstract Builder expectedAddresses(List<String> expectedAddresses);

        public abstract Builder expectedCertificates(List<String> expectedCertificates);

        public abstract PortalIntegrityExpectationRequest build();
    }
}
