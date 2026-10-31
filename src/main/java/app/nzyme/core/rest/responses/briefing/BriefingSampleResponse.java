package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingSampleResponse {

    @JsonProperty("label")
    public abstract String label();

    @JsonProperty("detail")
    @Nullable
    public abstract String detail();

    @JsonProperty("timestamp")
    @Nullable
    public abstract DateTime timestamp();

    @JsonProperty("severity")
    @Nullable
    public abstract String severity();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    public static BriefingSampleResponse create(String label, String detail, DateTime timestamp, String severity, BriefingLinkResponse link) {
        return builder()
                .label(label)
                .detail(detail)
                .timestamp(timestamp)
                .severity(severity)
                .link(link)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingSampleResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder label(String label);

        public abstract Builder detail(String detail);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder severity(String severity);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract BriefingSampleResponse build();
    }
}
