package app.nzyme.core.dashboards.db;

import app.nzyme.core.dashboards.DisplaySettings;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DisplayEntry {

    public abstract long id();
    public abstract UUID uuid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DisplaySettings settings();
    @Nullable
    public abstract String locationName();
    @Nullable
    public abstract DateTime lastSeenAt();
    @Nullable
    public abstract String lastRemoteAddress();
    @Nullable
    public abstract String lastReportedVersion();
    public abstract DateTime createdAt();
    public abstract DateTime updatedAt();

    public static DisplayEntry create(long id, UUID uuid, UUID organizationId, UUID tenantId, DisplaySettings settings, String locationName, DateTime lastSeenAt, String lastRemoteAddress, String lastReportedVersion, DateTime createdAt, DateTime updatedAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .settings(settings)
                .locationName(locationName)
                .lastSeenAt(lastSeenAt)
                .lastRemoteAddress(lastRemoteAddress)
                .lastReportedVersion(lastReportedVersion)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DisplayEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);
        public abstract Builder uuid(UUID uuid);
        public abstract Builder organizationId(UUID organizationId);
        public abstract Builder tenantId(UUID tenantId);
        public abstract Builder settings(DisplaySettings settings);
        public abstract Builder locationName(String locationName);
        public abstract Builder lastSeenAt(DateTime lastSeenAt);
        public abstract Builder lastRemoteAddress(String lastRemoteAddress);
        public abstract Builder lastReportedVersion(String lastReportedVersion);
        public abstract Builder createdAt(DateTime createdAt);
        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract DisplayEntry build();
    }

}
