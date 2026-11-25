package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.ethernet.spoofing.DhcpSpoofingDetector;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.TenantCacheKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class InternalAddresses {

    public static final CIDR CGNAT_NETWORK = CIDR.parse("100.64.0.0/10");

    public static final int MAX_INTERNAL_NETWORKS = 50;
    public static final int WIDEST_INTERNAL_NETWORK_IPV4 = 8;
    public static final int WIDEST_INTERNAL_NETWORK_IPV6 = 16;

    private static final List<CIDR> ALWAYS_INTERNAL = List.of(
            CIDR.parse("10.0.0.0/8"), CIDR.parse("172.16.0.0/12"), CIDR.parse("192.168.0.0/16"),
            CIDR.parse("169.254.0.0/16"), CIDR.parse("fc00::/7"), CIDR.parse("fe80::/10"), CIDR.parse("fec0::/10")
    );

    public static final int WIDEST_LEARNED_NETWORK = 48;
    public static final int MAX_LEARNED_NETWORKS = 500;

    public record Rules(boolean cgnatIsInternal, List<CIDR> internalNetworks, List<CIDR> learnedNetworks) {

        public static final Rules DEFAULT = new Rules(false, List.of());

        public Rules {
            internalNetworks = List.copyOf(internalNetworks);
            learnedNetworks = List.copyOf(learnedNetworks);
        }

        public Rules(boolean cgnatIsInternal, List<CIDR> internalNetworks) {
            this(cgnatIsInternal, internalNetworks, List.of());
        }

        public boolean isSiteLocal(InetAddress address) {
            return contains(ALWAYS_INTERNAL, address)
                    || (cgnatIsInternal && CGNAT_NETWORK.contains(address))
                    || contains(internalNetworks, address)
                    || (address instanceof Inet6Address && contains(learnedNetworks, address));
        }

        public List<CIDR> networks() {
            List<CIDR> networks = new ArrayList<>(ALWAYS_INTERNAL);
            if (cgnatIsInternal) {
                networks.add(CGNAT_NETWORK);
            }
            networks.addAll(internalNetworks);
            for (CIDR learned : learnedNetworks) {
                if (!networks.contains(learned)) {
                    networks.add(learned);
                }
            }

            return networks;
        }

        // The networks become part of the SQL as text. They only hold what CIDR prints.
        public String sqlCondition(String column) {
            return column + " <<= ANY('{" + networks().stream().map(CIDR::toString).collect(Collectors.joining(","))
                    + "}'::cidr[])";
        }

        private static boolean contains(List<CIDR> networks, InetAddress address) {
            for (CIDR network : networks) {
                if (network.contains(address)) {
                    return true;
                }
            }

            return false;
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

        String networks = nzyme.getDatabaseCoreRegistry()
                .getValue(EthernetRegistryKeys.INTERNAL_NETWORKS.key(), organizationId, tenantId)
                .orElse(null);

        String learned = nzyme.getDatabaseCoreRegistry()
                .getValue(EthernetRegistryKeys.LEARNED_INTERNAL_NETWORKS.key(), organizationId, tenantId)
                .orElse(null);

        return new Rules(
                Boolean.parseBoolean(value),
                parseNetworks(networks, MAX_INTERNAL_NETWORKS),
                parseNetworks(learned, MAX_LEARNED_NETWORKS)
        );
    }

    public boolean learnsNetworks(UUID organizationId, UUID tenantId) {
        return Boolean.parseBoolean(nzyme.getDatabaseCoreRegistry()
                .getValue(EthernetRegistryKeys.LEARN_INTERNAL_NETWORKS.key(), organizationId, tenantId)
                .orElse(EthernetRegistryKeys.LEARN_INTERNAL_NETWORKS.defaultValue().orElse("true")));
    }

    // Only the accepted configuration of trusted routers counts, so that a rogue advertisement cannot make an
    // address of the attacker internal.
    public void syncLearnedNetworks(UUID organizationId, UUID tenantId) {
        Rules before = read(organizationId, tenantId);

        List<CIDR> learned = List.of();
        if (learnsNetworks(organizationId, tenantId)) {
            List<String> advertised = nzyme.getDatabase().withHandle(handle ->
                    handle.createQuery("SELECT baseline->>'subnet_mask' FROM spoofing_dhcp_servers " +
                                    "WHERE organization_id = :organization_id AND tenant_id = :tenant_id " +
                                    "AND protocol = :protocol AND trusted AND baseline->>'subnet_mask' IS NOT NULL " +
                                    "ORDER BY first_seen ASC, id ASC")
                            .bind("organization_id", organizationId)
                            .bind("tenant_id", tenantId)
                            .bind("protocol", DhcpSpoofingDetector.PROTOCOL_ROUTER_ADVERTISEMENT)
                            .mapTo(String.class)
                            .list()
            );

            learned = learnedNetworks(advertised);
        }

        if (learned.equals(before.learnedNetworks())) {
            return;
        }

        nzyme.getDatabaseCoreRegistry().setValue(
                EthernetRegistryKeys.LEARNED_INTERNAL_NETWORKS.key(), serializeNetworks(learned), organizationId, tenantId
        );

        applyChange(before, organizationId, tenantId);
    }

    // One element per router, oldest first. The order is kept, so that at the limit the prefixes of the routers
    // learned first stay.
    public static List<CIDR> learnedNetworks(List<String> advertised) {
        Set<String> networks = new LinkedHashSet<>();
        for (String prefixes : advertised) {
            for (String prefix : prefixes.split(",")) {
                if (!prefix.contains("/")) {
                    continue;
                }

                try {
                    CIDR cidr = CIDR.parse(prefix.trim());
                    boolean ipv6 = cidr.address().getAddress().length == 16;

                    if (ipv6 && cidr.prefix() >= WIDEST_LEARNED_NETWORK && !Rules.DEFAULT.isSiteLocal(cidr.address())) {
                        networks.add(cidr.toString());
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return networks.stream().limit(MAX_LEARNED_NETWORKS).map(CIDR::parse).toList();
    }

    public void applyChange(Rules before, UUID organizationId, UUID tenantId) {
        Rules after = read(organizationId, tenantId);
        if (before.equals(after)) {
            return;
        }

        invalidate(organizationId, tenantId);

        nzyme.getEthernet().segmentation().invalidateResolver(organizationId, tenantId);
        nzyme.getDatabase().useHandle(handle -> {
            nzyme.getEthernet().segmentation().requestRestamp(handle, organizationId, tenantId);

            for (CIDR network : changedNetworks(before, after)) {
                nzyme.getEthernet().internalAddressRewrites().request(handle, organizationId, tenantId, network);
            }
        });
    }

    public static List<CIDR> changedNetworks(Rules before, Rules after) {
        List<CIDR> was = before.networks();
        List<CIDR> is = after.networks();

        List<CIDR> changed = new ArrayList<>();
        for (CIDR network : was) {
            if (!is.contains(network)) {
                changed.add(network);
            }
        }
        for (CIDR network : is) {
            if (!was.contains(network)) {
                changed.add(network);
            }
        }

        return changed;
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

    public static Rules tapSettings(@Nullable String tapConfiguration) {
        if (tapConfiguration == null || tapConfiguration.isBlank()) {
            return Rules.DEFAULT;
        }

        try {
            JsonNode misc = OM.readTree(tapConfiguration).path("misc");

            return new Rules(
                    misc.path("cgnat_is_internal").asBoolean(Rules.DEFAULT.cgnatIsInternal()),
                    parseNetworks(misc.path("internal_networks"), MAX_INTERNAL_NETWORKS)
            );
        } catch (JacksonException e) {
            return Rules.DEFAULT;
        }
    }

    public static String serializeNetworks(List<CIDR> networks) {
        return OM.writeValueAsString(networks.stream().map(CIDR::toString).toList());
    }

    // Lenient: this reads what is stored or what a tap reported. Input of users goes through validateNetworks().
    private static List<CIDR> parseNetworks(@Nullable String json, int limit) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return parseNetworks(OM.readTree(json), limit);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private static List<CIDR> parseNetworks(JsonNode node, int limit) {
        if (!node.isArray()) {
            return List.of();
        }

        Set<CIDR> networks = new LinkedHashSet<>();
        for (JsonNode element : node) {
            if (networks.size() >= limit) {
                break;
            }

            if (element.isString()) {
                try {
                    networks.add(CIDR.parse(element.asString().trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return List.copyOf(networks);
    }

    public static List<CIDR> validateNetworks(@Nullable List<String> networks) {
        if (networks == null) {
            throw new IllegalArgumentException("No networks.");
        }

        if (networks.size() > MAX_INTERNAL_NETWORKS) {
            throw new IllegalArgumentException("At most " + MAX_INTERNAL_NETWORKS + " networks.");
        }

        Set<CIDR> validated = new LinkedHashSet<>();
        for (String network : networks) {
            if (network == null || network.length() > 64 || !network.contains("/")) {
                throw new IllegalArgumentException("Not a network in CIDR notation, like 2001:db8:1200::/48.");
            }

            CIDR cidr = CIDR.parse(network.trim());
            boolean ipv4 = cidr.address().getAddress().length == 4;
            int widest = ipv4 ? WIDEST_INTERNAL_NETWORK_IPV4 : WIDEST_INTERNAL_NETWORK_IPV6;
            if (cidr.prefix() < widest) {
                throw new IllegalArgumentException("Network " + cidr + " is wider than /" + widest + ".");
            }

            validated.add(cidr);
        }

        return List.copyOf(validated);
    }

    public synchronized void invalidate(UUID organizationId, UUID tenantId) {
        generation++;
        byTenant.invalidate(TenantCacheKey.create(organizationId, tenantId));
    }

}
