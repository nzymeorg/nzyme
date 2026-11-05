package app.nzyme.core.rest.responses.ethernet.assets.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetProfileSummaryResponse {

    @JsonProperty("is_active")
    public abstract boolean isActive();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();
    @JsonProperty("active_alerts")
    public abstract long activeAlerts();
    @JsonProperty("total_alerts")
    public abstract long totalAlerts();
    @JsonProperty("exclusions")
    public abstract long exclusions();
    @Nullable
    @JsonProperty("last_change_at")
    public abstract DateTime lastChangeAt();
    @Nullable
    @JsonProperty("last_change_type")
    public abstract String lastChangeType();
    @Nullable
    @JsonProperty("last_change_value")
    public abstract String lastChangeValue();
    // Meaning depends on the type: attribute dimension, hostname or address source, or alert UUID.
    @Nullable
    @JsonProperty("last_change_detail")
    public abstract String lastChangeDetail();
    @JsonProperty("has_context")
    public abstract boolean hasContext();
    @JsonProperty("randomized_mac")
    public abstract boolean randomizedMac();
    @JsonProperty("taps")
    public abstract List<AssetTapResponse> taps();
    @JsonProperty("roles")
    public abstract List<AssetRoleResponse> roles();

    public static AssetProfileSummaryResponse create(boolean isActive, DateTime lastSeen, long activeAlerts, long totalAlerts, long exclusions, DateTime lastChangeAt, String lastChangeType, String lastChangeValue, String lastChangeDetail, boolean hasContext, boolean randomizedMac, List<AssetTapResponse> taps, List<AssetRoleResponse> roles) {
        return builder()
                .isActive(isActive)
                .lastSeen(lastSeen)
                .activeAlerts(activeAlerts)
                .totalAlerts(totalAlerts)
                .exclusions(exclusions)
                .lastChangeAt(lastChangeAt)
                .lastChangeType(lastChangeType)
                .lastChangeValue(lastChangeValue)
                .lastChangeDetail(lastChangeDetail)
                .hasContext(hasContext)
                .randomizedMac(randomizedMac)
                .taps(taps)
                .roles(roles)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetProfileSummaryResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder isActive(boolean isActive);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder activeAlerts(long activeAlerts);
        public abstract Builder totalAlerts(long totalAlerts);
        public abstract Builder exclusions(long exclusions);
        public abstract Builder lastChangeAt(DateTime lastChangeAt);
        public abstract Builder lastChangeType(String lastChangeType);
        public abstract Builder lastChangeValue(String lastChangeValue);

        public abstract Builder lastChangeDetail(String lastChangeDetail);
        public abstract Builder hasContext(boolean hasContext);
        public abstract Builder randomizedMac(boolean randomizedMac);
        public abstract Builder taps(List<AssetTapResponse> taps);
        public abstract Builder roles(List<AssetRoleResponse> roles);

        public abstract AssetProfileSummaryResponse build();
    }

}
