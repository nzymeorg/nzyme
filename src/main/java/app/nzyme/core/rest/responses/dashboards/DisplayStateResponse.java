package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DisplayStateResponse {

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("location")
    public abstract String location();

    @JsonProperty("dashboards")
    public abstract List<DisplayDashboardResponse> dashboards();

    @JsonProperty("theme")
    public abstract String theme();

    @Nullable
    @JsonProperty("light_from")
    public abstract String lightFrom();

    @Nullable
    @JsonProperty("light_until")
    public abstract String lightUntil();

    @JsonProperty("time_zone")
    public abstract String timeZone();

    @JsonProperty("clock_format")
    public abstract String clockFormat();

    @JsonProperty("scale_percent")
    public abstract int scalePercent();

    @JsonProperty("rotation_seconds")
    public abstract int rotationSeconds();

    @JsonProperty("refresh_seconds")
    public abstract int refreshSeconds();

    @JsonProperty("hide_cursor")
    public abstract boolean hideCursor();

    @JsonProperty("node_version")
    public abstract String nodeVersion();

    @JsonProperty("server_time")
    public abstract DateTime serverTime();

    public static DisplayStateResponse create(String name, String location, List<DisplayDashboardResponse> dashboards, String theme, String lightFrom, String lightUntil, String timeZone, String clockFormat, int scalePercent, int rotationSeconds, int refreshSeconds, boolean hideCursor, String nodeVersion, DateTime serverTime) {
        return builder()
                .name(name)
                .location(location)
                .dashboards(dashboards)
                .theme(theme)
                .lightFrom(lightFrom)
                .lightUntil(lightUntil)
                .timeZone(timeZone)
                .clockFormat(clockFormat)
                .scalePercent(scalePercent)
                .rotationSeconds(rotationSeconds)
                .refreshSeconds(refreshSeconds)
                .hideCursor(hideCursor)
                .nodeVersion(nodeVersion)
                .serverTime(serverTime)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplayStateResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);

        public abstract Builder location(String location);

        public abstract Builder dashboards(List<DisplayDashboardResponse> dashboards);

        public abstract Builder theme(String theme);

        public abstract Builder lightFrom(String lightFrom);

        public abstract Builder lightUntil(String lightUntil);

        public abstract Builder timeZone(String timeZone);

        public abstract Builder clockFormat(String clockFormat);

        public abstract Builder scalePercent(int scalePercent);

        public abstract Builder rotationSeconds(int rotationSeconds);

        public abstract Builder refreshSeconds(int refreshSeconds);

        public abstract Builder hideCursor(boolean hideCursor);

        public abstract Builder nodeVersion(String nodeVersion);

        public abstract Builder serverTime(DateTime serverTime);

        public abstract DisplayStateResponse build();
    }

}
