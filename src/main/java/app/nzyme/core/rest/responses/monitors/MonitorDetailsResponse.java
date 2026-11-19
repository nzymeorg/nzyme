package app.nzyme.core.rest.responses.monitors;

import app.nzyme.core.rest.responses.metrics.TimerResponse;
import app.nzyme.core.rest.responses.taps.TapHighLevelInformationDetailsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class MonitorDetailsResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("organization_id")
    public abstract UUID organizationId();

    @JsonProperty("tenant_id")
    public abstract UUID tenantId();

    @JsonProperty("enabled")
    public abstract boolean enabled();

    @JsonProperty("type")
    public abstract String type();

    @JsonProperty("name")
    public abstract String name();

    @Nullable
    @JsonProperty("description")
    public abstract String description();

    @Nullable
    @JsonProperty("taps")
    public abstract List<UUID> taps();

    @Nullable
    @JsonProperty("location_id")
    public abstract UUID locationId();

    @Nullable
    @JsonProperty("location_name")
    public abstract String locationName();

    @JsonProperty("trigger_condition")
    public abstract int triggerCondition();

    @JsonProperty("interval")
    public abstract int interval();

    @JsonProperty("lookback")
    public abstract int lookback();

    @JsonProperty("filters")
    public abstract String filters();

    @JsonProperty("alerted")
    public abstract boolean alerted();

    @JsonProperty("status")
    public abstract String status();

    @Nullable
    @JsonProperty("last_run")
    public abstract DateTime lastRun();

    @Nullable
    @JsonProperty("last_event")
    public abstract DateTime lastEvent();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    @JsonProperty("partial_data")
    public abstract boolean partialData();

    public static MonitorDetailsResponse create(UUID uuid, UUID organizationId, UUID tenantId, boolean enabled, String type, String name, String description, List<UUID> taps, UUID locationId, String locationName, int triggerCondition, int interval, int lookback, String filters, boolean alerted, String status, DateTime lastRun, DateTime lastEvent, DateTime createdAt, DateTime updatedAt, boolean partialData) {
        return builder()
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .enabled(enabled)
                .type(type)
                .name(name)
                .description(description)
                .taps(taps)
                .locationId(locationId)
                .locationName(locationName)
                .triggerCondition(triggerCondition)
                .interval(interval)
                .lookback(lookback)
                .filters(filters)
                .alerted(alerted)
                .status(status)
                .lastRun(lastRun)
                .lastEvent(lastEvent)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .partialData(partialData)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitorDetailsResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder enabled(boolean enabled);

        public abstract Builder type(String type);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder taps(List<UUID> taps);

        public abstract Builder locationId(UUID locationId);

        public abstract Builder locationName(String locationName);

        public abstract Builder triggerCondition(int triggerCondition);

        public abstract Builder interval(int interval);

        public abstract Builder lookback(int lookback);

        public abstract Builder filters(String filters);

        public abstract Builder alerted(boolean alerted);

        public abstract Builder status(String status);

        public abstract Builder lastRun(DateTime lastRun);

        public abstract Builder lastEvent(DateTime lastEvent);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract Builder partialData(boolean partialData);

        public abstract MonitorDetailsResponse build();
    }
}
