package app.nzyme.core.ethernet.dns.tunnels;

import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.dns.tunnels.db.DnsTunnelExclusionEntry;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.regex.Pattern;

public class DnsTunnelExclusionMatcher {

    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^(?=.{1,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\\.)*[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?$");

    @Nullable private final String domain;
    @Nullable private final CIDR clientCidr;

    private DnsTunnelExclusionMatcher(@Nullable String domain, @Nullable CIDR clientCidr) {
        this.domain = domain;
        this.clientCidr = clientCidr;
    }

    public static DnsTunnelExclusionMatcher create(@Nullable String domain, @Nullable String clientAddressCidr) {
        String normalizedDomain = normalizeDomain(domain);
        CIDR cidr = parseCidr(clientAddressCidr);

        if (normalizedDomain == null && cidr == null) {
            throw new IllegalArgumentException("At least one criterion is required.");
        }

        return new DnsTunnelExclusionMatcher(normalizedDomain, cidr);
    }

    public static DnsTunnelExclusionMatcher create(DnsTunnelExclusionEntry entry) {
        return create(entry.domain(), entry.clientAddressCidr());
    }

    // The domain matches the finding's domain exactly or as a parent domain.
    public boolean matches(@Nullable String findingDomain, @Nullable String clientAddress) {
        if (domain != null) {
            if (findingDomain == null) {
                return false;
            }

            String d = findingDomain.toLowerCase().trim();
            if (!d.equals(domain) && !d.endsWith("." + domain)) {
                return false;
            }
        }

        if (clientCidr != null && !cidrContains(clientCidr, clientAddress)) {
            return false;
        }

        return true;
    }

    @Nullable
    public static String normalizeDomain(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String domain = s.trim().toLowerCase();
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        while (domain.startsWith(".")) {
            domain = domain.substring(1);
        }

        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Domain [" + s.trim() + "] is not a valid domain name.");
        }

        return domain;
    }

    @Nullable
    static CIDR parseCidr(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String trimmed = s.trim();
        try {
            return CIDR.parse(trimmed);
        } catch (IllegalArgumentException e) {
            String reason = e.getMessage() != null && e.getMessage().startsWith("Host bits set")
                    ? "has host bits set. Use the network address, like 10.0.0.0/8."
                    : "is not a valid IP address or CIDR network.";
            throw new IllegalArgumentException("Client address [" + trimmed + "] " + reason);
        }
    }

    @Nullable
    public static String normalizeCidr(@Nullable String s) {
        CIDR cidr = parseCidr(s);
        if (cidr == null) {
            return null;
        }

        return cidr.isSingleHost() ? InetAddresses.toAddrString(cidr.address()) : cidr.toString();
    }

    static boolean cidrContains(CIDR cidr, @Nullable String address) {
        if (address == null) {
            return false;
        }

        InetAddress addr;
        try {
            addr = InetAddresses.forString(address);
        } catch (IllegalArgumentException e) {
            return false;
        }

        byte[] a = addr.getAddress();
        byte[] n = cidr.address().getAddress();
        if (a.length != n.length) {
            return false;
        }

        int prefix = cidr.prefix();
        for (int i = 0; i < prefix; i++) {
            int mask = 1 << (7 - i % 8);
            if ((a[i / 8] & mask) != (n[i / 8] & mask)) {
                return false;
            }
        }

        return true;
    }

}
