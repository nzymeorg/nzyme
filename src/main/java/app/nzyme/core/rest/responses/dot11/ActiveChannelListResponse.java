package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class ActiveChannelListResponse {

    @JsonProperty("channels")
    public abstract List<DeclaredChannelDetailsResponse> channels();

    @JsonProperty("tap_frequencies")
    public abstract List<ActiveChannelDetailsResponse> tapFrequencies();

    public static ActiveChannelListResponse create(List<DeclaredChannelDetailsResponse> channels,
                                                   List<ActiveChannelDetailsResponse> tapFrequencies) {
        return builder()
                .channels(channels)
                .tapFrequencies(tapFrequencies)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ActiveChannelListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder channels(List<DeclaredChannelDetailsResponse> channels);

        public abstract Builder tapFrequencies(List<ActiveChannelDetailsResponse> tapFrequencies);

        public abstract ActiveChannelListResponse build();
    }
}
