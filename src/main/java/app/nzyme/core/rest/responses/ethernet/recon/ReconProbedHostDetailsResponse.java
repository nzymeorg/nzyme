package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ReconProbedHostDetailsResponse {

    @JsonProperty("host")
    public abstract ReconProbedHostResponse host();

    @JsonProperty("sources")
    public abstract List<ReconProbedHostSourceResponse> sources();

    @JsonProperty("ports")
    public abstract List<ReconProbedHostPortResponse> ports();

    public static ReconProbedHostDetailsResponse create(ReconProbedHostResponse host, List<ReconProbedHostSourceResponse> sources, List<ReconProbedHostPortResponse> ports) {
        return builder()
                .host(host)
                .sources(sources)
                .ports(ports)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconProbedHostDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder host(ReconProbedHostResponse host);
        public abstract Builder sources(List<ReconProbedHostSourceResponse> sources);
        public abstract Builder ports(List<ReconProbedHostPortResponse> ports);
        public abstract ReconProbedHostDetailsResponse build();
    }
}
