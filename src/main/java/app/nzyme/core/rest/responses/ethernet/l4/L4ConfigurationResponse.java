package app.nzyme.core.rest.responses.ethernet.l4;

import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class L4ConfigurationResponse {

    @JsonProperty("cgnat_is_internal")
    public abstract ConfigurationEntryResponse cgnatIsInternal();

    public static L4ConfigurationResponse create(ConfigurationEntryResponse cgnatIsInternal) {
        return builder()
                .cgnatIsInternal(cgnatIsInternal)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_L4ConfigurationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder cgnatIsInternal(ConfigurationEntryResponse cgnatIsInternal);

        public abstract L4ConfigurationResponse build();
    }

}
