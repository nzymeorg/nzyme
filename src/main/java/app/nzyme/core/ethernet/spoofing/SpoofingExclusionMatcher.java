package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.spoofing.db.SpoofingExclusionEntry;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SpoofingExclusionMatcher {

    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");

    @Nullable private final Set<SpoofingType> types;
    @Nullable private final CIDR addressCidr;
    @Nullable private final String mac;

    private SpoofingExclusionMatcher(@Nullable Set<SpoofingType> types,
                                     @Nullable CIDR addressCidr,
                                     @Nullable String mac) {
        this.types = types;
        this.addressCidr = addressCidr;
        this.mac = mac;
    }

    public static SpoofingExclusionMatcher create(@Nullable Collection<String> types,
                                                  @Nullable String addressCidr,
                                                  @Nullable String mac) {
        Set<SpoofingType> parsedTypes = parseTypes(types);
        CIDR cidr = parseCidr(addressCidr);
        String normalizedMac = normalizeMac(mac);

        if (parsedTypes == null && cidr == null && normalizedMac == null) {
            throw new IllegalArgumentException("At least one criterion is required.");
        }

        return new SpoofingExclusionMatcher(parsedTypes, cidr, normalizedMac);
    }

    public static SpoofingExclusionMatcher create(SpoofingExclusionEntry entry) {
        return create(entry.types(), entry.addressCidr(), entry.mac());
    }

    public boolean matchesType(SpoofingType type) {
        return types == null || types.contains(type);
    }

    public boolean matches(SpoofingType type, @Nullable String address, @Nullable String deviceMac) {
        if (!matchesType(type)) {
            return false;
        }

        if (addressCidr != null && !cidrContains(addressCidr, address)) {
            return false;
        }

        if (mac != null && (deviceMac == null || !mac.equalsIgnoreCase(deviceMac.trim()))) {
            return false;
        }

        return true;
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
            throw new IllegalArgumentException("Address [" + trimmed + "] " + reason);
        }
    }

    @Nullable
    static Set<SpoofingType> parseTypes(@Nullable Collection<String> types) {
        if (types == null) {
            return null;
        }

        Set<SpoofingType> result = EnumSet.noneOf(SpoofingType.class);
        for (String type : types) {
            if (type == null || type.isBlank()) {
                continue;
            }

            try {
                result.add(SpoofingType.valueOf(type.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown spoofing type [" + type + "].");
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
            throw new IllegalArgumentException("MAC address [" + s.trim() + "] is not a valid MAC address.");
        }

        return mac;
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
