package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingAttentionItemResponse {

    @JsonProperty("severity")
    public abstract String severity();

    @JsonProperty("category")
    public abstract String category();

    @JsonProperty("subsystem")
    @Nullable
    public abstract String subsystem();

    @JsonProperty("title")
    public abstract String title();

    @JsonProperty("text")
    @Nullable
    public abstract String text();

    @JsonProperty("count")
    public abstract long count();

    @JsonProperty("timestamp")
    @Nullable
    public abstract DateTime timestamp();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    public static BriefingAttentionItemResponse create(String severity, String category, String subsystem, String title, String text, long count, DateTime timestamp, BriefingLinkResponse link) {
        return builder()
                .severity(severity)
                .category(category)
                .subsystem(subsystem)
                .title(title)
                .text(text)
                .count(count)
                .timestamp(timestamp)
                .link(link)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingAttentionItemResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder severity(String severity);

        public abstract Builder category(String category);

        public abstract Builder subsystem(String subsystem);

        public abstract Builder title(String title);

        public abstract Builder text(String text);

        public abstract Builder count(long count);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract BriefingAttentionItemResponse build();
    }
}
