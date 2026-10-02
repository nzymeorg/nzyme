package app.nzyme.core.context;

import com.google.auto.value.AutoValue;

import java.util.UUID;

@AutoValue
public abstract class IpAddressContextCacheKey {

    public abstract String ipAddress();
    public abstract UUID organizationId();
    public abstract UUID tenantId();

    public static IpAddressContextCacheKey create(String ipAddress, UUID organizationId, UUID tenantId) {
        return builder()
                .ipAddress(ipAddress)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_IpAddressContextCacheKey.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ipAddress(String ipAddress);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract IpAddressContextCacheKey build();
    }
}
