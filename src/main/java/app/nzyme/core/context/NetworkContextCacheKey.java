package app.nzyme.core.context;

import com.google.auto.value.AutoValue;

import java.net.InetAddress;
import java.util.UUID;

@AutoValue
public abstract class NetworkContextCacheKey {

    public abstract InetAddress ipAddress();
    public abstract UUID organizationId();
    public abstract UUID tenantId();

    public static NetworkContextCacheKey create(InetAddress ipAddress, UUID organizationId, UUID tenantId) {
        return builder()
                .ipAddress(ipAddress)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NetworkContextCacheKey.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ipAddress(InetAddress ipAddress);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract NetworkContextCacheKey build();
    }
}
