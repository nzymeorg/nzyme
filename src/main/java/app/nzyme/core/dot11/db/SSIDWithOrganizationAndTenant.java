package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

@AutoValue
public abstract class SSIDWithOrganizationAndTenant {

    public abstract String ssid();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DateTime lastSeen();
    public abstract int activeMinutes();
    public abstract List<UUID> taps();
    public abstract List<Sighting> sightings();

    // What the taps of one location, or the taps without a location, saw of the SSID.
    public record Sighting(int activeMinutes, List<UUID> taps) { }

    public static SSIDWithOrganizationAndTenant create(String ssid, UUID organizationId, UUID tenantId, DateTime lastSeen, int activeMinutes, List<UUID> taps, List<Sighting> sightings) {
        return builder()
                .ssid(ssid)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .lastSeen(lastSeen)
                .activeMinutes(activeMinutes)
                .taps(taps)
                .sightings(sightings)
                .build();
    }

    public abstract Builder toBuilder();

    public static Builder builder() {
        return new AutoValue_SSIDWithOrganizationAndTenant.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ssid(String ssid);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder activeMinutes(int activeMinutes);

        public abstract Builder taps(List<UUID> taps);

        public abstract Builder sightings(List<Sighting> sightings);

        public abstract SSIDWithOrganizationAndTenant build();
    }
}
