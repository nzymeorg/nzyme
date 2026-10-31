package app.nzyme.core.rest.responses.briefing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class BriefingTapResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("location_name")
    @Nullable
    public abstract String locationName();

    @JsonProperty("online")
    public abstract boolean online();

    @JsonProperty("last_report")
    @Nullable
    public abstract DateTime lastReport();

    @JsonProperty("version")
    @Nullable
    public abstract String version();

    @JsonProperty("clock_drift_ms")
    @Nullable
    public abstract Long clockDriftMs();

    @JsonProperty("active_alerts")
    public abstract long activeAlerts();

    @JsonProperty("captures")
    public abstract List<BriefingTapCaptureResponse> captures();

    public static BriefingTapResponse create(UUID uuid, String name, String locationName, boolean online, DateTime lastReport, String version, Long clockDriftMs, long activeAlerts, List<BriefingTapCaptureResponse> captures) {
        return builder()
                .uuid(uuid)
                .name(name)
                .locationName(locationName)
                .online(online)
                .lastReport(lastReport)
                .version(version)
                .clockDriftMs(clockDriftMs)
                .activeAlerts(activeAlerts)
                .captures(captures)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_BriefingTapResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder name(String name);

        public abstract Builder locationName(String locationName);

        public abstract Builder online(boolean online);

        public abstract Builder lastReport(DateTime lastReport);

        public abstract Builder version(String version);

        public abstract Builder clockDriftMs(Long clockDriftMs);

        public abstract Builder activeAlerts(long activeAlerts);

        public abstract Builder captures(List<BriefingTapCaptureResponse> captures);

        public abstract BriefingTapResponse build();
    }
}
