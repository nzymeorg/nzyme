package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class SpoofingConfigurationResponse {

    @JsonProperty("sections")
    public abstract List<SpoofingConfigurationSectionResponse> sections();

    public static SpoofingConfigurationResponse create(List<SpoofingConfigurationSectionResponse> sections) {
        return builder()
                .sections(sections)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SpoofingConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sections(List<SpoofingConfigurationSectionResponse> sections);

        public abstract SpoofingConfigurationResponse build();
    }
}
