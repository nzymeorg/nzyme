package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpUnreachablesListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("unreachables")
    public abstract List<IcmpUnreachableResponse> unreachables();

    public static IcmpUnreachablesListResponse create(long total, List<IcmpUnreachableResponse> unreachables) {
        return builder()
                .total(total)
                .unreachables(unreachables)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpUnreachablesListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder unreachables(List<IcmpUnreachableResponse> unreachables);
        public abstract IcmpUnreachablesListResponse build();
    }
}
