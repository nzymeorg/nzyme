package app.nzyme.core.ethernet.icmp;

import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.icmp.db.IcmpExclusionEntry;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.*;
import java.util.regex.Pattern;

public class IcmpExclusionMatcher {

    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");

    @Nullable private final Set<IcmpDetectionType> detectionTypes;
    @Nullable private final CIDR sourceCidr;
    @Nullable private final String sourceMac;
    @Nullable private final CIDR gatewayCidr;

    private IcmpExclusionMatcher(@Nullable Set<IcmpDetectionType> detectionTypes,
                                @Nullable CIDR sourceCidr,
                                @Nullable String sourceMac,
                                @Nullable CIDR gatewayCidr) {
        this.detectionTypes = detectionTypes;
        this.sourceCidr = sourceCidr;
        this.sourceMac = sourceMac;
        this.gatewayCidr = gatewayCidr;
    }

    public static IcmpExclusionMatcher create(@Nullable Collection<String> detectionTypes,
                                              @Nullable String sourceCidr,
                                              @Nullable String sourceMac,
                                              @Nullable String gatewayCidr) {
        Set<IcmpDetectionType> types = parseTypes(detectionTypes);
        CIDR srcCidr = parseCidr(sourceCidr, "Source address");
        String mac = normalizeMac(sourceMac);
        CIDR gwCidr = parseCidr(gatewayCidr, "Gateway address");

        if (types == null && srcCidr == null && mac == null && gwCidr == null) {
            throw new IllegalArgumentException("At least one criterion is required.");
        }

        return new IcmpExclusionMatcher(types, srcCidr, mac, gwCidr);
    }

    public static IcmpExclusionMatcher create(IcmpExclusionEntry entry) {
        return create(entry.detectionTypes(), entry.sourceCidr(), entry.sourceMac(), entry.gatewayCidr());
    }

    /**
     * @param gatewayAddress The advertised gateway for a redirect, or null for a traceroute. A gateway criterion
     *                       therefore never matches a traceroute.
     */
    public boolean matches(IcmpDetectionType type,
                           @Nullable String sourceAddress,
                           @Nullable String sourceMac,
                           @Nullable String gatewayAddress) {
        if (detectionTypes != null && !detectionTypes.contains(type)) {
            return false;
        }

        if (sourceCidr != null && !cidrContains(sourceCidr, sourceAddress)) {
            return false;
        }

        if (this.sourceMac != null && (sourceMac == null || !this.sourceMac.equalsIgnoreCase(sourceMac.trim()))) {
            return false;
        }

        if (gatewayCidr != null) {
            if (gatewayAddress == null || !cidrContains(gatewayCidr, gatewayAddress)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    static CIDR parseCidr(@Nullable String s, String label) {
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
            throw new IllegalArgumentException(label + " [" + trimmed + "] " + reason);
        }
    }

    @Nullable
    static Set<IcmpDetectionType> parseTypes(@Nullable Collection<String> types) {
        if (types == null) {
            return null;
        }

        Set<IcmpDetectionType> result = EnumSet.noneOf(IcmpDetectionType.class);
        for (String type : types) {
            if (type == null || type.isBlank()) {
                continue;
            }

            try {
                result.add(IcmpDetectionType.valueOf(type.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown detection type [" + type + "].");
            }
        }

        return result.isEmpty() ? null : result;
    }

    @Nullable
    public static String normalizeMac(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String mac = s.trim().toUpperCase().replace("-", ":");
        if (!MAC_PATTERN.matcher(mac).matches()) {
            throw new IllegalArgumentException("Source MAC address [" + s.trim() + "] is not a valid MAC address.");
        }

        return mac;
    }

    @Nullable
    public static String normalizeCidr(@Nullable String s) {
        CIDR cidr = parseCidr(s, "Address");
        if (cidr == null) {
            return null;
        }

        return cidr.isSingleHost() ? InetAddresses.toAddrString(cidr.address()) : cidr.toString();
    }

    static boolean cidrContains(CIDR cidr, @Nullable String address) {
        if (address == null || !BeaconDetector.isValidAddress(address)) {
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
