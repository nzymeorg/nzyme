package app.nzyme.core.assets.identity;

import app.nzyme.core.assets.identity.db.AssetIdentityExclusionEntry;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class AssetIdentityExclusionMatcher {

    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    private static final Pattern OUI_PATTERN = Pattern.compile("^([0-9A-F]{2}:){2}[0-9A-F]{2}$");

    private final String mac;
    private final boolean isPrefix;
    private final Set<String> types;

    private AssetIdentityExclusionMatcher(String mac, boolean isPrefix, Set<String> types) {
        this.mac = mac;
        this.isPrefix = isPrefix;
        this.types = types;
    }

    public static AssetIdentityExclusionMatcher create(@Nullable String mac, @Nullable List<String> types) {
        String normalizedMac = normalizeMac(mac);
        if (normalizedMac == null) {
            throw new IllegalArgumentException("A MAC address or vendor prefix is required.");
        }

        return new AssetIdentityExclusionMatcher(
                normalizedMac,
                OUI_PATTERN.matcher(normalizedMac).matches(),
                new LinkedHashSet<>(normalizeTypes(types))
        );
    }

    public static AssetIdentityExclusionMatcher create(AssetIdentityExclusionEntry entry) {
        return create(entry.mac(), entry.types());
    }

    public boolean matches(@Nullable String mac, AssetIdentityType type) {
        if (!matchesMac(mac)) {
            return false;
        }

        return types.isEmpty() || types.contains(type.name());
    }

    public boolean matchesMac(@Nullable String mac) {
        if (mac == null) {
            return false;
        }

        String m = mac.trim().toUpperCase();
        return isPrefix ? m.startsWith(this.mac + ":") : m.equals(this.mac);
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

    public static List<String> normalizeTypes(@Nullable List<String> types) {
        if (types == null || types.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                continue;
            }

            String t = type.trim().toUpperCase();
            try {
                result.add(AssetIdentityType.valueOf(t).name());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("[" + type.trim() + "] is not an asset identity detection " +
                        "type. Valid types are DUPLICATE_MAC, IDENTITY_CHANGE and MAC_CHANGE.");
            }
        }

        // All types selected is the same as no restriction.
        if (result.size() == AssetIdentityType.values().length) {
            return Collections.emptyList();
        }

        return List.copyOf(result);
    }

}
