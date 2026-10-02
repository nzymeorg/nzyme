package app.nzyme.core.context;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.IpAddressContextEntry;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.context.db.MacAddressTransparentContextEntry;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ContextService {

    public enum TransparentDataType {
        IP_ADDRESS,
        HOSTNAME
    }

    private final NzymeNode nzyme;

    private final Timer macLookupTimer;
    private final Timer ipLookupTimer;

    private final LoadingCache<MacAddressContextCacheKey, Optional<MacAddressContextEntry>> macAddressContextCache;
    private final LoadingCache<IpAddressContextCacheKey, Optional<IpAddressContextEntry>> ipAddressContextCache;

    public ContextService(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.macAddressContextCache = CacheBuilder.newBuilder()
                .maximumSize(2500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public Optional<MacAddressContextEntry> load(@NotNull MacAddressContextCacheKey key) {
                        return findMacAddressContextNoCache(key.macAddress(), key.organizationId(), key.tenantId());
                    }
                });

        this.ipAddressContextCache = CacheBuilder.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public Optional<IpAddressContextEntry> load(@NotNull IpAddressContextCacheKey key) {
                        return findIpAddressContextNoCache(key.ipAddress(), key.organizationId(), key.tenantId());
                    }
                });

        nzyme.getMetrics().register(MetricNames.CONTEXT_MAC_CACHE_SIZE, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return macAddressContextCache.size();
            }
        });
        nzyme.getMetrics().register(MetricNames.CONTEXT_IP_CACHE_SIZE, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return ipAddressContextCache.size();
            }
        });

        this.macLookupTimer = nzyme.getMetrics().timer(MetricNames.CONTEXT_MAC_LOOKUP_TIMING);
        this.ipLookupTimer = nzyme.getMetrics().timer(MetricNames.CONTEXT_IP_LOOKUP_TIMING);
    }

    public void invalidateMacAddressCache() {
        macAddressContextCache.invalidateAll();
    }

    public void invalidateIpAddressCache() {
        ipAddressContextCache.invalidateAll();
    }

    public long createMacAddressContext(String macAddress,
                                        String name,
                                        @Nullable String description,
                                        @Nullable String notes,
                                        UUID organizationId,
                                        UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
            handle.createQuery("INSERT INTO context_mac_addresses(mac_address, uuid, name, description, " +
                            "notes, organization_id, tenant_id, created_at, updated_at) VALUES(:mac_address, " +
                            ":uuid, :name, :description, :notes, :organization_id, :tenant_id, NOW(), NOW()) " +
                            "RETURNING id")
                    .bind("mac_address", macAddress.toUpperCase())
                    .bind("uuid", UUID.randomUUID())
                    .bind("name", name)
                    .bind("description", description)
                    .bind("notes", notes)
                    .bind("organization_id", organizationId)
                    .bind("tenant_id", tenantId)
                    .mapTo(Long.class)
                    .one()
        );
    }

    public List<MacAddressContextEntry> findAllMacAddressContext(UUID organizationId,
                                                                 UUID tenantId,
                                                                 String addressFilter,
                                                                 int limit,
                                                                 int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM context_mac_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac_address LIKE :address_filter " +
                                "ORDER BY mac_address ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("address_filter", addressFilter)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(MacAddressContextEntry.class)
                        .list()
        );
    }

    public Optional<MacAddressContextEntry> findMacAddressContext(String mac,
                                                                  @Nullable UUID organizationId,
                                                                  @Nullable UUID tenantId) {
        if (mac == null) {
            return Optional.empty();
        }

        try {
            return macAddressContextCache.get(MacAddressContextCacheKey.create(mac, organizationId, tenantId));
        } catch(ExecutionException e) {
            throw new RuntimeException("Could not MAC address context from cache.", e);
        }
    }

    public Optional<MacAddressContextEntry> findMacAddressContextNoCache(String mac,
                                                                         @Nullable UUID organizationId,
                                                                         @Nullable UUID tenantId) {
        if (mac == null) {
            return Optional.empty();
        }

        try(Timer.Context ignored = macLookupTimer.time()) {
            if (organizationId != null && tenantId != null) {
                // Tenant data.
                return nzyme.getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT * FROM context_mac_addresses " +
                                        "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                        "AND mac_address = :mac_address")
                                .bind("organization_id", organizationId)
                                .bind("tenant_id", tenantId)
                                .bind("mac_address", mac)
                                .mapTo(MacAddressContextEntry.class)
                                .findOne()
                );
            }

            if (organizationId != null) {
                // Organization data.
                return nzyme.getDatabase().withHandle(handle ->
                        handle.createQuery("SELECT * FROM context_mac_addresses " +
                                        "WHERE organization_id = :organization_id " +
                                        "AND mac_address = :mac_address")
                                .bind("organization_id", organizationId)
                                .bind("mac_address", mac)
                                .mapTo(MacAddressContextEntry.class)
                                .findFirst()
                );
            }

            // Any data.
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT * FROM context_mac_addresses " +
                                    "WHERE mac_address = :mac_address")
                            .bind("mac_address", mac)
                            .mapTo(MacAddressContextEntry.class)
                            .findFirst()
            );
        }
    }

    public Optional<MacAddressContextEntry> findMacAddressContext(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM context_mac_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", uuid)
                        .mapTo(MacAddressContextEntry.class)
                        .findOne()
        );
    }

    public Long countMacAddressContext(UUID organizationId, UUID tenantId, String addressFilter) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM context_mac_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND mac_address LIKE :address_filter")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("address_filter", addressFilter)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public void updateMacAddressContext(UUID uuid,
                                        UUID organizationId,
                                        UUID tenantId,
                                        String name,
                                        String description,
                                        String notes) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE context_mac_addresses SET name = :name, description = :description, " +
                                "notes = :notes, updated_at = NOW() WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("name", name)
                        .bind("description", description)
                        .bind("notes", notes)
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void updateMacAddressContextName(String mac, UUID organizationId, UUID tenantId, String name) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE context_mac_addresses SET name = :name, " +
                                "updated_at = NOW() WHERE mac_address = :mac AND organization_id = :organization_id " +
                                "AND tenant_id = :tenant_id")
                        .bind("name", name)
                        .bind("mac", mac)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteMacAddressContext(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM context_mac_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", uuid)
                        .execute()
        );
    }

    public List<MacAddressTransparentContextEntry> findTransparentMacAddressContext(long contextId) {
        return nzyme.getDatabase().withHandle(handle ->
                findTransparentMacAddressContext(handle, contextId)
        );
    }

    public List<MacAddressTransparentContextEntry> findTransparentMacAddressContext(Handle handle, long contextId) {
        return handle.createQuery("SELECT * FROM context_mac_addresses_transparent " +
                        "WHERE context_id = :context_id ORDER BY last_seen DESC")
                .bind("context_id", contextId)
                .mapTo(MacAddressTransparentContextEntry.class)
                .list();
    }

    public void registerTransparentMacAddressHostname(Handle handle,
                                                      long contextId,
                                                      UUID tapId,
                                                      String source,
                                                      String hostname,
                                                      DateTime lastSeen) {
        handle.createUpdate("INSERT INTO context_mac_addresses_transparent(context_id, tap_uuid, " +
                        "type, hostname, source, last_seen, created_at) VALUES(:context_id, :tap_uuid, :type, " +
                        ":hostname, :source, :last_seen, :last_seen)")
                .bind("context_id", contextId)
                .bind("tap_uuid", tapId)
                .bind("type", TransparentDataType.HOSTNAME)
                .bind("hostname", hostname)
                .bind("source", source)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void registerTransparentMacAddressIpAddress(Handle handle,
                                                       long contextId,
                                                       UUID tapId,
                                                       String source,
                                                       InetAddress ipAddress,
                                                       DateTime lastSeen) {
        handle.createUpdate("INSERT INTO context_mac_addresses_transparent(context_id, tap_uuid, " +
                        "type, ip_address, source, last_seen, created_at) VALUES(:context_id, :tap_uuid, :type, " +
                        ":ip_address::inet, :source, :last_seen, :last_seen)")
                .bind("context_id", contextId)
                .bind("tap_uuid", tapId)
                .bind("type", TransparentDataType.IP_ADDRESS)
                .bind("ip_address", ipAddress)
                .bind("source", source)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void touchTransparentMacAddressIpAddress(Handle handle,
                                                    long contextId,
                                                    UUID tapId,
                                                    String source,
                                                    InetAddress ipAddress,
                                                    DateTime lastSeen) {
        handle.createUpdate("UPDATE context_mac_addresses_transparent SET last_seen = :last_seen " +
                        "WHERE context_id = :context_id AND tap_uuid = :tap_uuid AND type = :type " +
                        "AND ip_address = :ip_address AND source = :source")
                .bind("context_id", contextId)
                .bind("tap_uuid", tapId)
                .bind("type", TransparentDataType.IP_ADDRESS)
                .bind("ip_address", ipAddress)
                .bind("source", source)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void touchTransparentMacAddressHostname(Handle handle,
                                                   long contextId,
                                                   UUID tapId,
                                                   String source,
                                                   String hostname,
                                                   DateTime lastSeen) {
        handle.createUpdate("UPDATE context_mac_addresses_transparent SET last_seen = :last_seen " +
                        "WHERE context_id = :context_id AND tap_uuid = :tap_uuid AND type = :type " +
                        "AND hostname = :hostname AND source = :source")
                .bind("context_id", contextId)
                .bind("tap_uuid", tapId)
                .bind("type", TransparentDataType.HOSTNAME)
                .bind("hostname", hostname)
                .bind("source", source)
                .bind("last_seen", lastSeen)
                .execute();
    }

    public void retentionCleanTransparentMacContext(DateTime cutoff) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM context_mac_addresses_transparent WHERE last_seen < :cutoff")
                        .bind("cutoff", cutoff)
                        .execute()
        );
    }


    public Optional<IpAddressContextEntry> findIpAddressContext(InetAddress ip,
                                                                @Nullable UUID organizationId,
                                                                @Nullable UUID tenantId) {
        if (ip == null) {
            return Optional.empty();
        }

        try {
            return ipAddressContextCache.get(IpAddressContextCacheKey.create(ip.getHostAddress(), organizationId, tenantId));
        } catch(ExecutionException e) {
            throw new RuntimeException("Could not load IP address context from cache.", e);
        }
    }

    public Optional<IpAddressContextEntry> findIpAddressContextNoCache(String ip,
                                                                       UUID organizationId,
                                                                       UUID tenantId) {
        if (ip == null) {
            return Optional.empty();
        }

        try(Timer.Context ignored = ipLookupTimer.time()) {
            return nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT * FROM context_ip_addresses " +
                                    "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                    "AND ip_address = :ip_address::inet")
                            .bind("organization_id", organizationId)
                            .bind("tenant_id", tenantId)
                            .bind("ip_address", ip)
                            .mapTo(IpAddressContextEntry.class)
                            .findOne()
            );
        }
    }

    public Optional<IpAddressContextEntry> findIpAddressContext(UUID uuid, UUID organizationId, UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM context_ip_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", uuid)
                        .mapTo(IpAddressContextEntry.class)
                        .findOne()
        );
    }

    public Long countIpAddressContext(UUID organizationId, UUID tenantId, String cidrFilter) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM context_ip_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND ip_address <<= :cidr_filter::inet")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cidr_filter", cidrFilter)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<IpAddressContextEntry> findAllIpAddressContext(UUID organizationId,
                                                               UUID tenantId,
                                                               String cidrFilter,
                                                               int limit,
                                                               int offset) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM context_ip_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND ip_address <<= :cidr_filter::inet " +
                                "ORDER BY ip_address ASC LIMIT :limit OFFSET :offset")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("cidr_filter", cidrFilter)
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(IpAddressContextEntry.class)
                        .list()
        );
    }

    public long createIpAddressContext(InetAddress ipAddress,
                                       String name,
                                       @Nullable String description,
                                       @Nullable String notes,
                                       UUID organizationId,
                                       UUID tenantId) {
        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("INSERT INTO context_ip_addresses(ip_address, uuid, name, description, " +
                                "notes, organization_id, tenant_id, created_at, updated_at) VALUES(:ip_address::inet, " +
                                ":uuid, :name, :description, :notes, :organization_id, :tenant_id, NOW(), NOW()) " +
                                "RETURNING id")
                        .bind("ip_address", ipAddress.getHostAddress())
                        .bind("uuid", UUID.randomUUID())
                        .bind("name", name)
                        .bind("description", description)
                        .bind("notes", notes)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .mapTo(Long.class)
                        .one()
        );
    }

    public void updateIpAddressContext(UUID uuid,
                                       UUID organizationId,
                                       UUID tenantId,
                                       String name,
                                       String description,
                                       String notes) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("UPDATE context_ip_addresses SET name = :name, description = :description, " +
                                "notes = :notes, updated_at = NOW() WHERE uuid = :uuid " +
                                "AND organization_id = :organization_id AND tenant_id = :tenant_id")
                        .bind("name", name)
                        .bind("description", description)
                        .bind("notes", notes)
                        .bind("uuid", uuid)
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .execute()
        );
    }

    public void deleteIpAddressContext(UUID uuid, UUID organizationId, UUID tenantId) {
        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("DELETE FROM context_ip_addresses " +
                                "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                "AND uuid = :uuid")
                        .bind("organization_id", organizationId)
                        .bind("tenant_id", tenantId)
                        .bind("uuid", uuid)
                        .execute()
        );
    }

}
