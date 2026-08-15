package app.nzyme.core.rest.responses.ethernet.nat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NATSTUNNegotiationsListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("negotiations")
    public abstract List<NATSTUNNegotiationDetailsResponse> negotiations();

    public static NATSTUNNegotiationsListResponse create(long total, List<NATSTUNNegotiationDetailsResponse> negotiations) {
        return builder()
                .total(total)
                .negotiations(negotiations)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NATSTUNNegotiationsListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder negotiations(List<NATSTUNNegotiationDetailsResponse> negotiations);

        public abstract NATSTUNNegotiationsListResponse build();
    }
}
