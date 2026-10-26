package app.nzyme.core.rest.responses.ethernet.recon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ReconProbedHostsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("hosts")
    public abstract List<ReconProbedHostResponse> hosts();

    public static ReconProbedHostsListResponse create(long total, List<ReconProbedHostResponse> hosts) {
        return builder()
                .total(total)
                .hosts(hosts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ReconProbedHostsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder hosts(List<ReconProbedHostResponse> hosts);
        public abstract ReconProbedHostsListResponse build();
    }
}
