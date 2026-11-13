package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AlertsWidgetAlertResponse {

    @JsonProperty("title")
    public abstract String title();

    @Nullable
    @JsonProperty("subsystem")
    public abstract String subsystem();

    @Nullable
    @JsonProperty("details")
    public abstract String details();

    @Nullable
    @JsonProperty("tap_name")
    public abstract String tapName();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AlertsWidgetAlertResponse create(String title, String subsystem, String details, String tapName, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .title(title)
                .subsystem(subsystem)
                .details(details)
                .tapName(tapName)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AlertsWidgetAlertResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder title(String title);

        public abstract Builder subsystem(String subsystem);

        public abstract Builder details(String details);

        public abstract Builder tapName(String tapName);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AlertsWidgetAlertResponse build();
    }

}
