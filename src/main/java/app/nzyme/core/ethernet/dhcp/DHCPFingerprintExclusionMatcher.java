package app.nzyme.core.ethernet.dhcp;

import app.nzyme.core.ethernet.dhcp.db.DHCPFingerprintExclusionEntry;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class DHCPFingerprintExclusionMatcher {

    public static final List<String> TRANSACTION_TYPES = List.of("Initial", "Renew", "Reboot", "Rebind");

    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    private static final Pattern OUI_PATTERN = Pattern.compile("^([0-9A-F]{2}:){2}[0-9A-F]{2}$");

    private final String mac;
    private final boolean isPrefix;
    private final Set<String> transactionTypes;

    private DHCPFingerprintExclusionMatcher(String mac, boolean isPrefix, Set<String> transactionTypes) {
        this.mac = mac;
        this.isPrefix = isPrefix;
        this.transactionTypes = transactionTypes;
    }

    public static DHCPFingerprintExclusionMatcher create(@Nullable String mac, @Nullable List<String> transactionTypes) {
        String normalizedMac = normalizeMac(mac);
        if (normalizedMac == null) {
            throw new IllegalArgumentException("A MAC address or vendor prefix is required.");
        }

        return new DHCPFingerprintExclusionMatcher(
                normalizedMac,
                OUI_PATTERN.matcher(normalizedMac).matches(),
                new LinkedHashSet<>(normalizeTransactionTypes(transactionTypes))
        );
    }

    public static DHCPFingerprintExclusionMatcher create(DHCPFingerprintExclusionEntry entry) {
        return create(entry.mac(), entry.transactionTypes());
    }

    public boolean matches(@Nullable String clientMac, @Nullable String transactionType) {
        if (!matchesMac(clientMac)) {
            return false;
        }

        if (transactionTypes.isEmpty()) {
            return true;
        }

        return transactionType != null && transactionTypes.contains(transactionType);
    }

    public boolean matchesMac(@Nullable String clientMac) {
        if (clientMac == null) {
            return false;
        }

        String m = clientMac.trim().toUpperCase();
        return isPrefix ? m.startsWith(mac + ":") : m.equals(mac);
    }

    public boolean isPrefix() {
        return isPrefix;
    }

    public String mac() {
        return mac;
    }

    @Nullable
    public static String normalizeMac(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String mac = s.trim().toUpperCase().replace("-", ":");
        if (!MAC_PATTERN.matcher(mac).matches() && !OUI_PATTERN.matcher(mac).matches()) {
            throw new IllegalArgumentException("[" + s.trim() + "] is not a valid MAC address like " +
                    "AA:BB:CC:DD:EE:FF or vendor prefix like AA:BB:CC.");
        }

        return mac;
    }

    public static List<String> normalizeTransactionTypes(@Nullable List<String> types) {
        if (types == null || types.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                continue;
            }

            String t = type.trim();
            String canonical = TRANSACTION_TYPES.stream()
                    .filter(x -> x.equalsIgnoreCase(t))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("[" + t + "] is not a DHCP transaction " +
                            "type. Valid types are " + String.join(", ", TRANSACTION_TYPES) + "."));
            result.add(canonical);
        }

        // All types selected is the same as no restriction.
        if (result.size() == TRANSACTION_TYPES.size()) {
            return Collections.emptyList();
        }

        return List.copyOf(result);
    }

}
