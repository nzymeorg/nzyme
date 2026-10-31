package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class BriefingLocationResponse {

    @JsonProperty("id")
    public abstract UUID id();

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("taps_total")
    public abstract long tapsTotal();

    @JsonProperty("taps_online")
    public abstract long tapsOnline();

    @JsonProperty("offline_taps")
    public abstract List<String> offlineTaps();

    // Null when the user may not view alerts.
    @JsonProperty("alert_count")
    @Nullable
    public abstract Long alertCount();

    // Null without coordinates or environment data.
    @JsonProperty("environment")
    @Nullable
    public abstract BriefingLocationEnvironmentResponse environment();

    @JsonProperty("link")
    public abstract BriefingLinkResponse link();

    public static BriefingLocationResponse create(UUID id, String name, long tapsTotal, long tapsOnline, List<String> offlineTaps, Long alertCount, BriefingLocationEnvironmentResponse environment, BriefingLinkResponse link) {
        return builder()
                .id(id)
                .name(name)
                .tapsTotal(tapsTotal)
                .tapsOnline(tapsOnline)
                .offlineTaps(offlineTaps)
                .alertCount(alertCount)
                .environment(environment)
                .link(link)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingLocationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(UUID id);

        public abstract Builder name(String name);

        public abstract Builder tapsTotal(long tapsTotal);

        public abstract Builder tapsOnline(long tapsOnline);

        public abstract Builder offlineTaps(List<String> offlineTaps);

        public abstract Builder alertCount(Long alertCount);

        public abstract Builder environment(BriefingLocationEnvironmentResponse environment);

        public abstract Builder link(BriefingLinkResponse link);

        public abstract BriefingLocationResponse build();
    }
}
