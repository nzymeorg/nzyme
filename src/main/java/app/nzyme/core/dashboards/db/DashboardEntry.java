package app.nzyme.core.dashboards.db;

import app.nzyme.core.dashboards.DashboardSettings;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DashboardEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DashboardSettings settings();
    public abstract long displayCount();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DashboardEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, DashboardSettings settings, long displayCount, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .settings(settings)
                .displayCount(displayCount)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DashboardEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder settings(DashboardSettings settings);
        public abstract Builder displayCount(long displayCount);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract DashboardEntry build();
    }

}
