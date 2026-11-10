package app.nzyme.core.rest.responses.ethernet.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;

@AutoValue
public abstract class TopologySummaryResponse {

    @Schema(description = "Distinct internal addresses.")
    @JsonProperty("internal_hosts")
    public abstract long internalHosts();

    @Schema(description = "Distinct external addresses.")
    @JsonProperty("external_hosts")
    public abstract long externalHosts();

    @Schema(description = "Distinct autonomous systems of external addresses.")
    @JsonProperty("external_networks")
    public abstract long externalNetworks();

    @Schema(description = "Distinct countries of external addresses.")
    @JsonProperty("external_countries")
    public abstract long externalCountries();

    public static TopologySummaryResponse create(long internalHosts, long externalHosts, long externalNetworks,
                                                 long externalCountries) {
        return builder()
                .internalHosts(internalHosts)
                .externalHosts(externalHosts)
                .externalNetworks(externalNetworks)
                .externalCountries(externalCountries)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TopologySummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder internalHosts(long internalHosts);
        public abstract Builder externalHosts(long externalHosts);
        public abstract Builder externalNetworks(long externalNetworks);
        public abstract Builder externalCountries(long externalCountries);

        public abstract TopologySummaryResponse build();
    }

}
