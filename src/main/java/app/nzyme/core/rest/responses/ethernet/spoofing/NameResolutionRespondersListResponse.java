package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NameResolutionRespondersListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("responders")
    public abstract List<NameResolutionResponderResponse> responders();

    public static NameResolutionRespondersListResponse create(long total, List<NameResolutionResponderResponse> responders) {
        return builder()
                .total(total)
                .responders(responders)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionRespondersListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder responders(List<NameResolutionResponderResponse> responders);
        public abstract NameResolutionRespondersListResponse build();
    }
}
