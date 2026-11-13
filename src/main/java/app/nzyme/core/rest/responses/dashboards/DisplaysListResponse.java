package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class DisplaysListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("displays")
    public abstract List<DisplayResponse> displays();

    public static DisplaysListResponse create(long total, List<DisplayResponse> displays) {
        return builder()
                .total(total)
                .displays(displays)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplaysListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder displays(List<DisplayResponse> displays);

        public abstract DisplaysListResponse build();
    }

}
