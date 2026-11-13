package app.nzyme.core.rest.requests;

import app.nzyme.core.dashboards.DashboardWidget;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

@Schema(description = "Body for creating or updating a dashboard.")
@AutoValue
public abstract class DashboardRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract String scopeType();

    @Nullable
    public abstract List<UUID> scopeIds();

    @Nullable
    public abstract Integer windowMinutes();

    @Nullable
    public abstract List<DashboardWidget> widgets();

    @JsonCreator
    public static DashboardRequest create(@Schema(description = "Name of the dashboard. Up to 128 characters.", requiredMode = Schema.RequiredMode.REQUIRED)
                                          @JsonProperty("name") String name,
                                          @Schema(description = "Optional description of the dashboard.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("description") String description,
                                          @Schema(description = "Which taps the dashboard reads data from. ALL for every tap, LOCATIONS for all taps at the locations in scope_ids, TAPS for the taps in scope_ids. Defaults to ALL.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("scope_type") String scopeType,
                                          @Schema(description = "UUIDs of the locations or taps of the scope. Up to 100. Ignored for the scope ALL.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("scope_ids") List<UUID> scopeIds,
                                          @Schema(description = "Time window of the dashboard in minutes. One of 60, 240, 1440 or 10080. Defaults to 1440.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("window_minutes") Integer windowMinutes,
                                          @Schema(description = "The widgets of the dashboard, placed on a grid of 12 columns and 6 rows. Up to 24. Each widget has a unique id, a type (STATUS, ALERTS, TAPS, METRIC, CLOCK, LOCATIONS, TEXT or TOPOLOGY), its position x and y, its size w and h and options. All widgets accept the option title, ALERTS and the alert metrics accept the option subsystem (ETHERNET, DOT11 or BLUETOOTH) to only cover the alerts of that subsystem, METRIC needs the option metric, CLOCK needs the option time_zone, an IANA time zone like Europe/Berlin, and TEXT needs the option text, up to 2000 characters that are shown as they are, and accepts the option size (SMALL, MEDIUM or LARGE). TOPOLOGY accepts the options filters, a JSON encoded filter definition as a string with the fields of the TCP/UDP sessions list, internal_level (HOST, SUBNET or TAP), external_level (HOST, NETWORK or COUNTRY) and connections (50, 100 or 250).", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                          @JsonProperty("widgets") List<DashboardWidget> widgets) {
        return builder()
                .name(name)
                .description(description)
                .scopeType(scopeType)
                .scopeIds(scopeIds)
                .windowMinutes(windowMinutes)
                .widgets(widgets)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder description(String description);
        public abstract Builder scopeType(String scopeType);
        public abstract Builder scopeIds(List<UUID> scopeIds);
        public abstract Builder windowMinutes(Integer windowMinutes);
        public abstract Builder widgets(List<DashboardWidget> widgets);

        public abstract DashboardRequest build();
    }

}
