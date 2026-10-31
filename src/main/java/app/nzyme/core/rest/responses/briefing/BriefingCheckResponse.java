package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class BriefingCheckResponse {

    @JsonProperty("key")
    public abstract String key();

    @JsonProperty("level")
    public abstract String level();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("detail")
    @Nullable
    public abstract String detail();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    @JsonProperty("subsystem")
    @Nullable
    public abstract String subsystem();

    public static BriefingCheckResponse create(String key, String level, String title, String detail, BriefingLinkResponse link) {
        return create(key, level, title, detail, link, null);
    }

    public static BriefingCheckResponse create(String key, String level, String title, String detail, BriefingLinkResponse link, String subsystem) {
        return builder()
                .key(key)
                .level(level)
                .title(title)
                .detail(detail)
                .link(link)
                .subsystem(subsystem)
                .build();
    }

    public BriefingCheckResponse withSubsystem(String subsystem) {
        return create(key(), level(), title(), detail(), link(), subsystem);
    }

    public static Builder builder() {
        return new AutoValue_BriefingCheckResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder key(String key);

        public abstract Builder level(String level);

        public abstract Builder title(String title);

        public abstract Builder detail(String detail);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract Builder subsystem(String subsystem);

        public abstract BriefingCheckResponse build();
    }
}
