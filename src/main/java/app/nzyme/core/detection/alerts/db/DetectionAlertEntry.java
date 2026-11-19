package app.nzyme.core.detection.alerts.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class DetectionAlertEntry {

    public abstract long id();
    public abstract UUID uuid();

    public abstract boolean isResolved();

    @Nullable
    public abstract UUID dot11MonitoredNetworkId();

    @Nullable
    public abstract UUID tapId();

    @Nullable
    public abstract UUID locationId();

    public abstract String detectionType();
    public abstract String subsystem();
    public abstract String details();
    public abstract DateTime createdAt();
    public abstract DateTime lastSeen();
    public abstract String comparisonChecksum();

    @Nullable
    public abstract UUID organizationId();

    @Nullable
    public abstract UUID tenantId();

    public static DetectionAlertEntry create(long id, UUID uuid, boolean isResolved, UUID dot11MonitoredNetworkId, UUID tapId, UUID locationId, String detectionType, String subsystem, String details, DateTime createdAt, DateTime lastSeen, String comparisonChecksum, UUID organizationId, UUID tenantId) {
        return builder()
                .id(id)
                .uuid(uuid)
                .isResolved(isResolved)
                .dot11MonitoredNetworkId(dot11MonitoredNetworkId)
                .tapId(tapId)
                .locationId(locationId)
                .detectionType(detectionType)
                .subsystem(subsystem)
                .details(details)
                .createdAt(createdAt)
                .lastSeen(lastSeen)
                .comparisonChecksum(comparisonChecksum)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DetectionAlertEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder isResolved(boolean isResolved);

        public abstract Builder dot11MonitoredNetworkId(UUID dot11MonitoredNetworkId);

        public abstract Builder tapId(UUID tapId);

        public abstract Builder locationId(UUID locationId);

        public abstract Builder detectionType(String detectionType);

        public abstract Builder subsystem(String subsystem);

        public abstract Builder details(String details);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder comparisonChecksum(String comparisonChecksum);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract DetectionAlertEntry build();
    }
}
