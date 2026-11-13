package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class TapsWidgetCellResponse {

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("state")
    public abstract String state();

    public static TapsWidgetCellResponse create(String name, String state) {
        return builder()
                .name(name)
                .state(state)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TapsWidgetCellResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder state(String state);

        public abstract TapsWidgetCellResponse build();
    }

}
