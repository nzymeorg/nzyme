package app.nzyme.core.monitors.db;

import app.nzyme.core.security.authentication.TenantScopedEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class MonitorEntry implements TenantScopedEntity {

    // This is (de-)serialized and passed along tasks.

    @JsonProperty("id")
    public abstract Long id();
    @JsonProperty("uuid")
    public abstract UUID uuid();
    @JsonProperty("organization_id")
    public abstract UUID organizationId();
    @JsonProperty("tenant_id")
    public abstract UUID tenantId();
    @JsonProperty("enabled")
    public abstract Boolean enabled();
    @JsonProperty("type")
    public abstract String type();
    @JsonProperty("name")
    public abstract String name();
    @JsonProperty("description")
    @Nullable
    public abstract String description();
    @JsonProperty("taps")
    @Nullable
    public abstract List<UUID> taps();
    @JsonProperty("location_id")
    @Nullable
    public abstract UUID locationId();
    @JsonProperty("trigger_condition")
    public abstract Integer triggerCondition();
    @JsonProperty("interval")
    public abstract Integer interval();
    @JsonProperty("lookback")
    public abstract Integer lookback();
    @JsonProperty("filters")
    public abstract String filters();
    @JsonProperty("alerted")
    public abstract Boolean alerted();
    @JsonProperty("status")
    public abstract String status();
    @JsonProperty("status_changed_at")
    @Nullable
    public abstract DateTime statusChangedAt();
    @JsonProperty("last_run")
    @Nullable
    public abstract DateTime lastRun();
    @JsonProperty("last_event")
    @Nullable
    public abstract DateTime lastEvent();
    @JsonProperty("created_at")
    public abstract DateTime createdAt();
    @JsonProperty("updated_at")
    public abstract DateTime updatedAt();

    @JsonCreator
    public static MonitorEntry create(@JsonProperty("id") Long id,
                                      @JsonProperty("uuid") UUID uuid,
                                      @JsonProperty("organization_id") UUID organizationId,
                                      @JsonProperty("tenant_id") UUID tenantId,
                                      @JsonProperty("enabled") Boolean enabled,
                                      @JsonProperty("type") String type,
                                      @JsonProperty("name") String name,
                                      @JsonProperty("description") String description,
                                      @JsonProperty("taps") List<UUID> taps,
                                      @JsonProperty("location_id") UUID locationId,
                                      @JsonProperty("trigger_condition") Integer triggerCondition,
                                      @JsonProperty("interval") Integer interval,
                                      @JsonProperty("lookback") Integer lookback,
                                      @JsonProperty("filters") String filters,
                                      @JsonProperty("alerted") Boolean alerted,
                                      @JsonProperty("status") String status,
                                      @JsonProperty("status_changed_at") DateTime statusChangedAt,
                                      @JsonProperty("last_run") DateTime lastRun,
                                      @JsonProperty("last_event") DateTime lastEvent,
                                      @JsonProperty("created_at") DateTime createdAt,
                                      @JsonProperty("updated_at") DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .enabled(enabled)
                .type(type)
                .name(name)
                .description(description)
                .taps(taps)
                .locationId(locationId)
                .triggerCondition(triggerCondition)
                .interval(interval)
                .lookback(lookback)
                .filters(filters)
                .alerted(alerted)
                .status(status)
                .statusChangedAt(statusChangedAt)
                .lastRun(lastRun)
                .lastEvent(lastEvent)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitorEntry.Builder();
    }
    
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(Long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder enabled(Boolean enabled);

        public abstract Builder type(String type);

        public abstract Builder name(String name);

        public abstract Builder description(String description);

        public abstract Builder taps(List<UUID> taps);

        public abstract Builder locationId(UUID locationId);

        public abstract Builder triggerCondition(Integer triggerCondition);

        public abstract Builder interval(Integer interval);

        public abstract Builder lookback(Integer lookback);

        public abstract Builder filters(String filters);

        public abstract Builder alerted(Boolean alerted);

        public abstract Builder status(String status);

        public abstract Builder statusChangedAt(DateTime statusChangedAt);

        public abstract Builder lastRun(DateTime lastRun);

        public abstract Builder lastEvent(DateTime lastEvent);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract MonitorEntry build();
    }
}
