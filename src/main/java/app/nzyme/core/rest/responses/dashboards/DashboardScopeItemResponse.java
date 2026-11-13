package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.UUID;

@AutoValue
public abstract class DashboardScopeItemResponse {

    @JsonProperty("id")
    public abstract UUID id();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("detail")
    public abstract String detail();

    public static DashboardScopeItemResponse create(UUID id, String name, String detail) {
        return builder()
                .id(id)
                .name(name)
                .detail(detail)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardScopeItemResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(UUID id);

        public abstract Builder name(String name);

        public abstract Builder detail(String detail);

        public abstract DashboardScopeItemResponse build();
    }

}
