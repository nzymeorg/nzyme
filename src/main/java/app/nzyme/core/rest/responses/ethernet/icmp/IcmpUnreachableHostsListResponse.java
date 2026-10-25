package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpUnreachableHostsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("hosts")
    public abstract List<IcmpUnreachableHostResponse> hosts();

    public static IcmpUnreachableHostsListResponse create(long total, List<IcmpUnreachableHostResponse> hosts) {
        return builder()
                .total(total)
                .hosts(hosts)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpUnreachableHostsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder hosts(List<IcmpUnreachableHostResponse> hosts);
        public abstract IcmpUnreachableHostsListResponse build();
    }
}
