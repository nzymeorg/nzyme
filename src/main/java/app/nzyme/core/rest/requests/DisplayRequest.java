package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@Schema(description = "Body for creating or updating a display. A display is a screen that shows dashboards "
        + "without a user session. It holds the presentation settings of that screen and the dashboards it "
        + "rotates through.")
@AutoValue
public abstract class DisplayRequest {

    @Nullable
    public abstract String name();

    @Nullable
    public abstract UUID locationId();

    @Nullable
    public abstract List<UUID> dashboards();

    @Nullable
    public abstract String theme();

    @Nullable
    public abstract String lightFrom();

    @Nullable
    public abstract String lightUntil();

    @Nullable
    public abstract String timeZone();

    @Nullable
    public abstract String clockFormat();

    @Nullable
    public abstract Integer scalePercent();

    @Nullable
    public abstract Integer rotationSeconds();

    @Nullable
    public abstract Integer refreshSeconds();

    @Nullable
    public abstract List<String> allowedNetworks();

    @Nullable
    public abstract Boolean redacted();

    @Nullable
    public abstract Boolean hideCursor();

    @Nullable
    public abstract DateTime expiresAt();

    @JsonCreator
    public static DisplayRequest create(@Schema(description = "Name of the display. Up to 128 characters.", requiredMode = Schema.RequiredMode.REQUIRED)
                                        @JsonProperty("name") String name,
                                        @Schema(description = "UUID of the location the screen is at. The location must belong to the same tenant.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("location_id") UUID locationId,
                                        @Schema(description = "UUIDs of the dashboards the display shows, in rotation order. Up to 25 dashboards of the same tenant.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("dashboards") List<UUID> dashboards,
                                        @Schema(description = "DARK, LIGHT or SCHEDULED. Defaults to DARK.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("theme") String theme,
                                        @Schema(description = "Time of day in the format HH:mm at which a SCHEDULED theme switches to light, in the time zone of the display.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("light_from") String lightFrom,
                                        @Schema(description = "Time of day in the format HH:mm at which a SCHEDULED theme switches back to dark, in the time zone of the display.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("light_until") String lightUntil,
                                        @Schema(description = "IANA time zone the display renders times in, for example Europe/Berlin. Defaults to UTC.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("time_zone") String timeZone,
                                        @Schema(description = "H24 or H12. Defaults to H24.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("clock_format") String clockFormat,
                                        @Schema(description = "Size of the content in percent, between 50 and 200. Defaults to 100.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("scale_percent") Integer scalePercent,
                                        @Schema(description = "Seconds each dashboard stays on screen when the display shows more than one, between 5 and 3600. Defaults to 30.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("rotation_seconds") Integer rotationSeconds,
                                        @Schema(description = "Seconds between two requests of the display to Nzyme, between 10 and 3600. Defaults to 60.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("refresh_seconds") Integer refreshSeconds,
                                        @Schema(description = "IP addresses or CIDR networks the display may connect from. Up to 25. Empty or omitted allows every address. Matched against the address of the connection to the node and never against forwarding headers, so behind a reverse proxy this is the address of the proxy.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("allowed_networks") List<String> allowedNetworks,
                                        @Schema(description = "Whether the display hides addresses and names. A redacted display receives alerts without their details and a topology without addresses and names. Defaults to false for a new display and to the current value on an update.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("redacted") Boolean redacted,
                                        @Schema(description = "Whether the display hides the mouse pointer. Defaults to false.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("hide_cursor") Boolean hideCursor,
                                        @Schema(description = "Time after which the display is rejected. Omitted for a display that does not expire.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                                        @JsonProperty("expires_at") DateTime expiresAt) {
        return builder()
                .name(name)
                .locationId(locationId)
                .dashboards(dashboards)
                .theme(theme)
                .lightFrom(lightFrom)
                .lightUntil(lightUntil)
                .timeZone(timeZone)
                .clockFormat(clockFormat)
                .scalePercent(scalePercent)
                .rotationSeconds(rotationSeconds)
                .refreshSeconds(refreshSeconds)
                .allowedNetworks(allowedNetworks)
                .redacted(redacted)
                .hideCursor(hideCursor)
                .expiresAt(expiresAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplayRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder name(String name);
        public abstract Builder locationId(UUID locationId);
        public abstract Builder dashboards(List<UUID> dashboards);
        public abstract Builder theme(String theme);
        public abstract Builder lightFrom(String lightFrom);
        public abstract Builder lightUntil(String lightUntil);
        public abstract Builder timeZone(String timeZone);
        public abstract Builder clockFormat(String clockFormat);
        public abstract Builder scalePercent(Integer scalePercent);
        public abstract Builder rotationSeconds(Integer rotationSeconds);
        public abstract Builder refreshSeconds(Integer refreshSeconds);
        public abstract Builder allowedNetworks(List<String> allowedNetworks);
        public abstract Builder redacted(Boolean redacted);
        public abstract Builder hideCursor(Boolean hideCursor);
        public abstract Builder expiresAt(DateTime expiresAt);

        public abstract DisplayRequest build();
    }

}
