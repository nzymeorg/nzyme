package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Body for importing BSSIDs, channels and security suites into a monitored WiFi network.")
@AutoValue
public abstract class ImportMonitoredNetworkDataRequest {

    @NotNull
    public abstract List<ImportMonitoredNetworkDataBSSIDRequest> bssids();

    @NotNull
    public abstract List<Long> channels();

    @NotNull
    public abstract List<String> securitySuites();

    @JsonCreator
    public static ImportMonitoredNetworkDataRequest create(@Schema(description = "Expected BSSIDs of the network and their fingerprints.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("bssids") List<ImportMonitoredNetworkDataBSSIDRequest> bssids,
                                                           @Schema(description = "Expected channel frequencies in MHz.", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("channels") List<Long> channels,
                                                           @Schema(description = "Expected security suite identifiers like \"WPA2-PSK-CCMP/CCMP\".", requiredMode = Schema.RequiredMode.REQUIRED)
                                                           @JsonProperty("security_suites") List<String> securitySuites) {
        return builder()
                .bssids(bssids)
                .channels(channels)
                .securitySuites(securitySuites)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ImportMonitoredNetworkDataRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bssids(@NotNull List<ImportMonitoredNetworkDataBSSIDRequest> bssids);

        public abstract Builder channels(@NotNull List<Long> channels);

        public abstract Builder securitySuites(@NotNull List<String> securitySuites);

        public abstract ImportMonitoredNetworkDataRequest build();
    }

}
