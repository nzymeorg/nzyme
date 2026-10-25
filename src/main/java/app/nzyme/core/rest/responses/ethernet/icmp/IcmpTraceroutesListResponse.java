package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpTraceroutesListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("traceroutes")
    public abstract List<IcmpTracerouteResponse> traceroutes();

    public static IcmpTraceroutesListResponse create(long total, List<IcmpTracerouteResponse> traceroutes) {
        return builder()
                .total(total)
                .traceroutes(traceroutes)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpTraceroutesListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder traceroutes(List<IcmpTracerouteResponse> traceroutes);
        public abstract IcmpTraceroutesListResponse build();
    }
}
