package app.nzyme.core.ethernet.recon;

import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.beacons.BeaconDetector;
import app.nzyme.core.ethernet.recon.db.ReconExclusionEntry;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.*;
import java.util.regex.Pattern;

public class ReconExclusionMatcher {

    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");

    @Nullable private final Set<ReconScanType> scanTypes;
    @Nullable private final CIDR sourceCidr;
    @Nullable private final String sourceMac;
    @Nullable private final CIDR targetCidr;

    private ReconExclusionMatcher(@Nullable Set<ReconScanType> scanTypes,
                                  @Nullable CIDR sourceCidr,
                                  @Nullable String sourceMac,
                                  @Nullable CIDR targetCidr) {
        this.scanTypes = scanTypes;
        this.sourceCidr = sourceCidr;
        this.sourceMac = sourceMac;
        this.targetCidr = targetCidr;
    }

    public static ReconExclusionMatcher create(@Nullable Collection<String> scanTypes,
                                               @Nullable String sourceCidr,
                                               @Nullable String sourceMac,
                                               @Nullable String targetCidr) {
        Set<ReconScanType> types = parseTypes(scanTypes);
        CIDR srcCidr = parseCidr(sourceCidr, "Scanner address");
        String mac = normalizeMac(sourceMac);
        CIDR dstCidr = parseCidr(targetCidr, "Target network");

        if (types == null && srcCidr == null && mac == null && dstCidr == null) {
            throw new IllegalArgumentException("At least one criterion is required.");
        }

        return new ReconExclusionMatcher(types, srcCidr, mac, dstCidr);
    }

    public static ReconExclusionMatcher create(ReconExclusionEntry entry) {
        return create(entry.scanTypes(), entry.sourceCidr(), entry.sourceMac(), entry.targetCidr());
    }

    public boolean hasTargetCriteria() {
        return targetCidr != null;
    }

    @Nullable
    public CIDR targetCidr() {
        return targetCidr;
    }

    public boolean matchesType(@Nullable String type) {
        if (scanTypes == null) {
            return true;
        }

        ReconScanType t;
        try {
            t = type == null ? null : ReconScanType.valueOf(type);
        } catch (IllegalArgumentException e) {
            t = null;
        }

        return t != null && scanTypes.contains(t);
    }

    public boolean matchesSource(@Nullable String address, @Nullable String mac) {
        if (sourceCidr != null && !cidrContains(sourceCidr, address)) {
            return false;
        }

        if (sourceMac != null && (mac == null || !sourceMac.equalsIgnoreCase(mac.trim()))) {
            return false;
        }

        return true;
    }

    public boolean matchesTargets(@Nullable String targetAddress, @Nullable Collection<String> targetAddresses) {
        if (targetCidr == null) {
            return true;
        }

        if (targetAddress != null) {
            return cidrContains(targetCidr, targetAddress);
        }

        if (targetAddresses == null || targetAddresses.isEmpty()) {
            return false;
        }

        for (String address : targetAddresses) {
            if (!cidrContains(targetCidr, address)) {
                return false;
            }
        }

        return true;
    }

    public boolean matches(@Nullable String type,
                           @Nullable String sourceAddress,
                           @Nullable String sourceMac,
                           @Nullable String targetAddress,
                           @Nullable Collection<String> targetAddresses) {
        return matchesType(type) && matchesSource(sourceAddress, sourceMac)
                && matchesTargets(targetAddress, targetAddresses);
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
    static Set<ReconScanType> parseTypes(@Nullable Collection<String> types) {
        if (types == null) {
            return null;
        }

        Set<ReconScanType> result = EnumSet.noneOf(ReconScanType.class);
        for (String type : types) {
            if (type == null || type.isBlank()) {
                continue;
            }

            try {
                result.add(ReconScanType.valueOf(type.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown scan type [" + type + "].");
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
            throw new IllegalArgumentException("Scanner MAC address [" + s.trim() + "] is not a valid MAC address.");
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

        return cidr.contains(addr);
    }

}
