package app.nzyme.core.rest.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class DisplayResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("location_id")
    public abstract UUID locationId();

    @Nullable
    @JsonProperty("location_name")
    public abstract String locationName();

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

    @JsonProperty("allowed_networks")
    public abstract List<String> allowedNetworks();

    @JsonProperty("redacted")
    public abstract boolean redacted();

    @JsonProperty("hide_cursor")
    public abstract boolean hideCursor();

    @Nullable
    @JsonProperty("expires_at")
    public abstract DateTime expiresAt();

    @JsonProperty("expired")
    public abstract boolean expired();

    @JsonProperty("online")
    public abstract boolean online();

    @Nullable
    @JsonProperty("last_seen_at")
    public abstract DateTime lastSeenAt();

    @Nullable
    @JsonProperty("last_remote_address")
    public abstract String lastRemoteAddress();

    @Nullable
    @JsonProperty("last_reported_version")
    public abstract String lastReportedVersion();

    @JsonProperty("node_version")
    public abstract String nodeVersion();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    public static DisplayResponse create(UUID uuid, String name, UUID locationId, String locationName, List<DisplayDashboardResponse> dashboards, String theme, String lightFrom, String lightUntil, String timeZone, String clockFormat, int scalePercent, int rotationSeconds, int refreshSeconds, List<String> allowedNetworks, boolean redacted, boolean hideCursor, DateTime expiresAt, boolean expired, boolean online, DateTime lastSeenAt, String lastRemoteAddress, String lastReportedVersion, String nodeVersion, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .uuid(uuid)
                .name(name)
                .locationId(locationId)
                .locationName(locationName)
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
                .expired(expired)
                .online(online)
                .lastSeenAt(lastSeenAt)
                .lastRemoteAddress(lastRemoteAddress)
                .lastReportedVersion(lastReportedVersion)
                .nodeVersion(nodeVersion)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplayResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder name(String name);

        public abstract Builder locationId(UUID locationId);

        public abstract Builder locationName(String locationName);

        public abstract Builder dashboards(List<DisplayDashboardResponse> dashboards);

        public abstract Builder theme(String theme);

        public abstract Builder lightFrom(String lightFrom);

        public abstract Builder lightUntil(String lightUntil);

        public abstract Builder timeZone(String timeZone);

        public abstract Builder clockFormat(String clockFormat);

        public abstract Builder scalePercent(int scalePercent);

        public abstract Builder rotationSeconds(int rotationSeconds);

        public abstract Builder refreshSeconds(int refreshSeconds);

        public abstract Builder allowedNetworks(List<String> allowedNetworks);

        public abstract Builder redacted(boolean redacted);

        public abstract Builder hideCursor(boolean hideCursor);

        public abstract Builder expiresAt(DateTime expiresAt);

        public abstract Builder expired(boolean expired);

        public abstract Builder online(boolean online);

        public abstract Builder lastSeenAt(DateTime lastSeenAt);

        public abstract Builder lastRemoteAddress(String lastRemoteAddress);

        public abstract Builder lastReportedVersion(String lastReportedVersion);

        public abstract Builder nodeVersion(String nodeVersion);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract DisplayResponse build();
    }

}
