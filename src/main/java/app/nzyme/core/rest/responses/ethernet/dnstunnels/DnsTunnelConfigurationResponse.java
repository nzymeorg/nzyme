package app.nzyme.core.rest.responses.ethernet.dnstunnels;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class DnsTunnelConfigurationResponse {

    @JsonProperty("sections")
    public abstract List<DnsTunnelConfigurationSectionResponse> sections();

    public static DnsTunnelConfigurationResponse create(List<DnsTunnelConfigurationSectionResponse> sections) {
        return builder()
                .sections(sections)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTunnelConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sections(List<DnsTunnelConfigurationSectionResponse> sections);
        public abstract DnsTunnelConfigurationResponse build();
    }
}
