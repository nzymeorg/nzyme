package app.nzyme.core.rest.responses.ethernet.icmp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class IcmpPacketsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("packets")
    public abstract List<IcmpPacketResponse> packets();

    public static IcmpPacketsListResponse create(long total, List<IcmpPacketResponse> packets) {
        return builder()
                .total(total)
                .packets(packets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IcmpPacketsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder packets(List<IcmpPacketResponse> packets);
        public abstract IcmpPacketsListResponse build();
    }
}
