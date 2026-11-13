package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class TapsWidgetLocationResponse {

    @Nullable
    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("total")
    public abstract int total();

    @JsonProperty("online")
    public abstract int online();

    @JsonProperty("states")
    public abstract String states();

    public static TapsWidgetLocationResponse create(String name, int total, int online, String states) {
        return builder()
                .name(name)
                .total(total)
                .online(online)
                .states(states)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TapsWidgetLocationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder total(int total);

        public abstract Builder online(int online);

        public abstract Builder states(String states);

        public abstract TapsWidgetLocationResponse build();
    }

}
