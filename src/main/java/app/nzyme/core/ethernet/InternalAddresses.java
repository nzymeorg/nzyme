package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TenantCacheKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class InternalAddresses {

    public static final CIDR CGNAT_NETWORK = CIDR.parse("100.64.0.0/10");

    public record Rules(boolean cgnatIsInternal) {

        public static final Rules DEFAULT = new Rules(false);

        public boolean isSiteLocal(InetAddress address) {
            return address.isSiteLocalAddress() || (cgnatIsInternal && CGNAT_NETWORK.contains(address));
        }

    }

    private static final ObjectMapper OM = new ObjectMapper();

    private final NzymeNode nzyme;

    // Other nodes of a cluster follow a change when their entry expires.
    private final Cache<TenantCacheKey, Rules> byTenant = CacheBuilder.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    // A tap never moves to another tenant.
    private final Cache<UUID, TenantCacheKey> tenantOfTap = CacheBuilder.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();

    private long generation = 0;

    public InternalAddresses(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public Rules of(@Nullable UUID organizationId, @Nullable UUID tenantId) {
        if (organizationId == null || tenantId == null) {
            return Rules.DEFAULT;
        }

        TenantCacheKey key = TenantCacheKey.create(organizationId, tenantId);
        Rules cached = byTenant.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        long readAt;
        synchronized (this) {
            readAt = generation;
        }

        Rules rules = read(organizationId, tenantId);

        // A value that was read before a change must not be cached after it.
        synchronized (this) {
            if (readAt == generation) {
                byTenant.put(key, rules);
            }
        }

        return rules;
    }

    // Not cached. For what has to follow a change at once, also on a node that did not receive it.
    public Rules read(UUID organizationId, UUID tenantId) {
        String value = nzyme.getDatabaseCoreRegistry()
                .getValue(EthernetRegistryKeys.CGNAT_IS_INTERNAL.key(), organizationId, tenantId)
                .orElse(EthernetRegistryKeys.CGNAT_IS_INTERNAL.defaultValue().orElse("false"));

        return new Rules(Boolean.parseBoolean(value));
    }

    public Rules ofTap(Tap tap) {
        return of(tap.organizationId(), tap.tenantId());
    }

    public Rules ofTap(UUID tapUuid) {
        TenantCacheKey tenant = tenantOfTap.getIfPresent(tapUuid);
        if (tenant != null) {
            return of(tenant.organization(), tenant.tenant());
        }

        Optional<Tap> tap = nzyme.getTapManager().findTap(tapUuid);
        if (tap.isEmpty() || tap.get().organizationId() == null || tap.get().tenantId() == null) {
            return Rules.DEFAULT;
        }

        tenantOfTap.put(tapUuid, TenantCacheKey.create(tap.get().organizationId(), tap.get().tenantId()));

        return ofTap(tap.get());
    }

    // What a tap reported as its own setting. A tap that reports none runs with the default.
    public static boolean tapSetting(@Nullable String tapConfiguration) {
        if (tapConfiguration == null || tapConfiguration.isBlank()) {
            return Rules.DEFAULT.cgnatIsInternal();
        }

        try {
            return OM.readTree(tapConfiguration).path("misc").path("cgnat_is_internal")
                    .asBoolean(Rules.DEFAULT.cgnatIsInternal());
        } catch (JacksonException e) {
            return Rules.DEFAULT.cgnatIsInternal();
        }
    }

    public synchronized void invalidate(UUID organizationId, UUID tenantId) {
        generation++;
        byTenant.invalidate(TenantCacheKey.create(organizationId, tenantId));
    }

}
