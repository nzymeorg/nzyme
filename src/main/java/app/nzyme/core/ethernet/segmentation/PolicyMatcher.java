package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

// Flows that are read back from the rollup do not always carry everything a selector asks for. Where a selector
// cannot be decided the verdict is INDETERMINATE. Flows that are evaluated as they are recorded never get it.
public class PolicyMatcher {

    public enum Verdict {
        OUT_OF_SCOPE, ALLOWED, VIOLATION, INDETERMINATE
    }

    // The destination port of a flow that stands for many ports.
    public static final int FOLDED_PORT = -1;

    private enum Match {
        YES, NO, UNKNOWN;

        static Match of(boolean value) {
            return value ? YES : NO;
        }
    }

    private record CompiledSelector(PolicyRule.SelectorType type,
                                    @Nullable UUID uuid,
                                    @Nullable CIDR network,
                                    @Nullable Long asn,
                                    @Nullable String countryCode) {

        boolean namesBroadcast() {
            return network != null && isBroadcastRange(network);
        }

    }

    private record CompiledRule(List<CompiledSelector> sources,
                                List<CompiledSelector> destinations,
                                List<PolicyRule.Service> services) { }

    private final Policy policy;
    private final CompiledRule scope;
    private final List<CompiledRule> allowed;

    public PolicyMatcher(Policy policy) {
        this(policy, policy.scope(), policy.allowed());
    }

    public PolicyMatcher(@Nullable Policy policy, PolicyRule scope, List<PolicyRule> allowed) {
        this.policy = policy;
        this.scope = compile(scope);
        this.allowed = Lists.newArrayList();
        for (PolicyRule rule : allowed) {
            this.allowed.add(compile(rule));
        }
    }

    @Nullable
    public Policy policy() {
        return policy;
    }

    public Verdict evaluate(Endpoint source, Endpoint destination, String l4Type, int port, @Nullable String protocol) {
        Match inScope = matches(scope, source, destination, l4Type, port, protocol, true);
        if (inScope == Match.NO) {
            return Verdict.OUT_OF_SCOPE;
        }

        if (inScope == Match.UNKNOWN) {
            return Verdict.INDETERMINATE;
        }

        boolean unknown = false;
        for (CompiledRule rule : allowed) {
            Match match = matches(rule, source, destination, l4Type, port, protocol, false);
            if (match == Match.YES) {
                return Verdict.ALLOWED;
            }

            if (match == Match.UNKNOWN) {
                unknown = true;
            }
        }

        return unknown ? Verdict.INDETERMINATE : Verdict.VIOLATION;
    }

    public int allowedBy(Endpoint source, Endpoint destination, String l4Type, int port, @Nullable String protocol) {
        for (int i = 0; i < allowed.size(); i++) {
            if (matches(allowed.get(i), source, destination, l4Type, port, protocol, false) == Match.YES) {
                return i;
            }
        }

        return -1;
    }

    private static Match matches(CompiledRule rule,
                                 Endpoint source,
                                 Endpoint destination,
                                 String l4Type,
                                 int port,
                                 @Nullable String protocol,
                                 boolean scope) {
        Match sources = matchesSide(rule.sources(), source, scope);
        if (sources == Match.NO) {
            return Match.NO;
        }

        Match destinations = matchesSide(rule.destinations(), destination, scope);
        if (destinations == Match.NO) {
            return Match.NO;
        }

        Match services = matchesServices(rule.services(), l4Type, port, protocol);
        if (services == Match.NO) {
            return Match.NO;
        }

        return sources == Match.YES && destinations == Match.YES && services == Match.YES ? Match.YES : Match.UNKNOWN;
    }

    private static Match matchesSide(List<CompiledSelector> selectors, Endpoint endpoint, boolean scope) {
        if (selectors.isEmpty()) {
            // Anything, which in a scope means any host.
            return Match.of(!(scope && endpoint.broadcast()));
        }

        boolean unknown = false;
        for (CompiledSelector selector : selectors) {
            Match match = endpoint.broadcast()
                    ? matchesBroadcast(selector, endpoint, scope) : matchesSelector(selector, endpoint);
            if (match == Match.YES) {
                return Match.YES;
            }

            if (match == Match.UNKNOWN) {
                unknown = true;
            }
        }

        return unknown ? Match.UNKNOWN : Match.NO;
    }

    // A scope takes a broadcast or multicast address in only by naming it. An allowed crossing covers it with any
    // destination or any address range it lies in.
    private static Match matchesBroadcast(CompiledSelector selector, Endpoint endpoint, boolean scope) {
        switch (selector.type()) {
            case BROADCAST:
                return Match.YES;
            case ANY:
                return Match.of(!scope);
            case ADDRESS:
                if (scope && !selector.namesBroadcast()) {
                    return Match.NO;
                }
                return endpoint.hasAddress() ? Match.of(selector.network().contains(endpoint.address())) : Match.UNKNOWN;
            default:
                return Match.NO;
        }
    }

    // The broadcast address of a subnet is not recognized, because it cannot be told from a host without knowing the
    // subnet.
    public static boolean isBroadcastRange(CIDR network) {
        if (network.address().getAddress().length == 4) {
            return network.prefix() == 32
                    ? isBroadcastAddress(network.address())
                    : network.prefix() >= 4 && network.address().isMulticastAddress();
        }

        return network.prefix() >= 8 && network.address().isMulticastAddress();
    }

    public static boolean isBroadcastAddress(InetAddress address) {
        if (address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }

        for (byte b : bytes) {
            if (b != (byte) 0xFF) {
                return false;
            }
        }

        return true;
    }

    private static Match matchesSelector(CompiledSelector selector, Endpoint endpoint) {
        switch (selector.type()) {
            case ANY:
                return Match.YES;
            case INTERNAL:
                return Match.of(endpoint.internal());
            case EXTERNAL:
                return Match.of(!endpoint.internal());
            case UNASSIGNED:
                return Match.of(endpoint.internal() && endpoint.segment() == null);
            case SEGMENT:
                return Match.of(endpoint.segment() != null && endpoint.segment().uuid().equals(selector.uuid()));
            case TAG:
                if (!endpoint.internal()) {
                    return Match.NO;
                }
                return endpoint.hasAddress() ? Match.of(endpoint.tags().contains(selector.uuid())) : Match.UNKNOWN;
            case ASSET:
                if (!endpoint.internal()) {
                    return Match.NO;
                }
                return endpoint.hasAddress() ? Match.of(selector.uuid().equals(endpoint.asset())) : Match.UNKNOWN;
            case ADDRESS:
                if (selector.namesBroadcast()) {
                    // No host has such an address, also not one that was stored without its address.
                    return Match.NO;
                }
                return endpoint.hasAddress() ? Match.of(selector.network().contains(endpoint.address())) : Match.UNKNOWN;
            case ASN:
                return Match.of(!endpoint.internal() && selector.asn().equals(endpoint.asn()));
            case COUNTRY:
                return Match.of(!endpoint.internal() && selector.countryCode().equalsIgnoreCase(endpoint.countryCode()));
            default:
                return Match.NO;
        }
    }

    private static Match matchesServices(List<PolicyRule.Service> services, String l4Type, int port, @Nullable String protocol) {
        if (services.isEmpty()) {
            return Match.YES;
        }

        boolean unknown = false;
        for (PolicyRule.Service service : services) {
            Match match = matchesService(service, l4Type, port, protocol);
            if (match == Match.YES) {
                return Match.YES;
            }

            if (match == Match.UNKNOWN) {
                unknown = true;
            }
        }

        return unknown ? Match.UNKNOWN : Match.NO;
    }

    private static Match matchesService(PolicyRule.Service service, String l4Type, int port, @Nullable String protocol) {
        if (service.l4Type() != null && !service.l4Type().equalsIgnoreCase(l4Type)) {
            return Match.NO;
        }

        if (service.protocol() != null && !speaks(protocol, service.protocol())) {
            return Match.NO;
        }

        if (service.portFrom() == null) {
            return Match.YES;
        }

        if (port == FOLDED_PORT) {
            return Match.UNKNOWN;
        }

        int to = service.portTo() == null ? service.portFrom() : service.portTo();
        return Match.of(port >= service.portFrom() && port <= to);
    }

    static boolean speaks(@Nullable String flowProtocol, String protocol) {
        if (flowProtocol == null || flowProtocol.isEmpty()) {
            return false;
        }

        for (String spoken : flowProtocol.split("/")) {
            if (spoken.equalsIgnoreCase(protocol)) {
                return true;
            }
        }

        return false;
    }

    private static CompiledRule compile(PolicyRule rule) {
        return new CompiledRule(compile(rule.sources()), compile(rule.destinations()), rule.services());
    }

    private static List<CompiledSelector> compile(List<PolicyRule.Selector> selectors) {
        List<CompiledSelector> compiled = Lists.newArrayList();
        for (PolicyRule.Selector selector : selectors) {
            switch (selector.type()) {
                case SEGMENT:
                case TAG:
                case ASSET:
                    compiled.add(new CompiledSelector(selector.type(), UUID.fromString(selector.value()), null, null, null));
                    break;
                case ADDRESS:
                    compiled.add(new CompiledSelector(selector.type(), null, CIDR.parse(selector.value()), null, null));
                    break;
                case ASN:
                    compiled.add(new CompiledSelector(selector.type(), null, null, Long.parseLong(selector.value()), null));
                    break;
                case COUNTRY:
                    compiled.add(new CompiledSelector(selector.type(), null, null, null, selector.value()));
                    break;
                default:
                    compiled.add(new CompiledSelector(selector.type(), null, null, null, null));
            }
        }

        return compiled;
    }

}
