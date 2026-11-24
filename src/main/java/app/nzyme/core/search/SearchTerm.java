package app.nzyme.core.search;

import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.net.InetAddress;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchTerm {

    public static final int MAXIMUM_LENGTH = 128;
    public static final int MINIMUM_TEXT_LENGTH = 2;
    public static final int MINIMUM_RECORDED_TEXT_LENGTH = 3;

    public enum Kind {
        MAC, MAC_PREFIX, IP, CIDR, UUID, HASH, JA4, TEXT
    }

    private static final Pattern MAC_SEPARATED = Pattern.compile("^[0-9a-f]{2}([:-][0-9a-f]{2}){5}$");
    private static final Pattern MAC_DOTTED = Pattern.compile("^[0-9a-f]{4}\\.[0-9a-f]{4}\\.[0-9a-f]{4}$");
    private static final Pattern MAC_BARE = Pattern.compile("^[0-9a-f]{12}$");
    private static final Pattern MAC_PREFIX = Pattern.compile("^[0-9a-f]{2}([:-][0-9a-f]{2}){1,4}[:-]?$");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{16,128}$");
    private static final Pattern JA4 = Pattern.compile("^[tqd][0-9a-z]{9}_[0-9a-f]{12}_[0-9a-f]{12}$");
    private static final Pattern CIDR = Pattern.compile("^(.+)/(\\d{1,3})$");

    private final String raw;
    private final String lower;
    private final Set<Kind> kinds = EnumSet.noneOf(Kind.class);

    @Nullable
    private String mac;
    @Nullable
    private String macPrefix;
    @Nullable
    private String address;
    @Nullable
    private String cidr;
    @Nullable
    private UUID uuid;

    private SearchTerm(String raw) {
        this.raw = raw;
        this.lower = raw.toLowerCase(Locale.ROOT);
    }

    public static Optional<SearchTerm> parse(@Nullable String query) {
        if (query == null) {
            return Optional.empty();
        }

        StringBuilder cleaned = new StringBuilder();
        for (char c : query.toCharArray()) {
            cleaned.append(Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c);
        }

        String raw = cleaned.toString().trim().replaceAll(" {2,}", " ");
        if (raw.length() < MINIMUM_TEXT_LENGTH || raw.length() > MAXIMUM_LENGTH) {
            return Optional.empty();
        }

        SearchTerm term = new SearchTerm(raw);
        term.classify();

        return Optional.of(term);
    }

    private void classify() {
        if (MAC_SEPARATED.matcher(lower).matches() || MAC_DOTTED.matcher(lower).matches()
                || MAC_BARE.matcher(lower).matches()) {
            String hex = lower.replaceAll("[^0-9a-f]", "").toUpperCase(Locale.ROOT);
            mac = String.join(":", hex.split("(?<=\\G..)"));
            kinds.add(Kind.MAC);
        } else if (MAC_PREFIX.matcher(lower).matches()) {
            String hex = lower.replaceAll("[^0-9a-f]", "").toUpperCase(Locale.ROOT);
            macPrefix = String.join(":", hex.split("(?<=\\G..)")) + ":";
            kinds.add(Kind.MAC_PREFIX);
        }

        InetAddress parsed = literal(raw);
        if (parsed != null) {
            address = InetAddresses.toAddrString(parsed);
            kinds.add(Kind.IP);
        } else {
            Matcher m = CIDR.matcher(raw);
            InetAddress network = m.matches() ? literal(m.group(1)) : null;
            if (network != null) {
                int prefix = Integer.parseInt(m.group(2));
                if (prefix <= network.getAddress().length * 8) {
                    cidr = InetAddresses.toAddrString(network) + "/" + prefix;
                    kinds.add(Kind.CIDR);
                }
            }
        }

        if (UUID_PATTERN.matcher(lower).matches()) {
            uuid = UUID.fromString(lower);
            kinds.add(Kind.UUID);
        }

        if (HASH.matcher(lower).matches()) {
            kinds.add(Kind.HASH);
        }

        if (JA4.matcher(lower).matches()) {
            kinds.add(Kind.JA4);
        }

        kinds.add(Kind.TEXT);
    }

    /*
     * Guava parses literals only. InetAddress.getByName would resolve names through DNS. An address with a zone,
     * like fe80::1%eth0, stays text: PostgreSQL does not take it as an address and the node stores none.
     */
    @Nullable
    private static InetAddress literal(String value) {
        if (value.contains("%") || !InetAddresses.isInetAddress(value)) {
            return null;
        }

        try {
            return InetAddresses.forString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String raw() {
        return raw;
    }

    public String lower() {
        return lower;
    }

    public boolean is(Kind kind) {
        return kinds.contains(kind);
    }

    public boolean isAnyOf(Kind... candidates) {
        for (Kind kind : candidates) {
            if (kinds.contains(kind)) {
                return true;
            }
        }

        return false;
    }

    // An address of some kind. Names that only contain it are rarely what is looked for.
    public boolean isIdentifier() {
        return isAnyOf(Kind.MAC, Kind.IP, Kind.CIDR, Kind.UUID);
    }

    public boolean isRecordedText() {
        return !isIdentifier() && raw.length() >= MINIMUM_RECORDED_TEXT_LENGTH;
    }

    public String mac() {
        return mac;
    }

    public String macPrefix() {
        return macPrefix;
    }

    public String address() {
        return address;
    }

    public String cidr() {
        return cidr;
    }

    // Finds the address in text without finding longer addresses that contain it, like 11.1.1.10 for 1.1.1.1.
    public String addressInText() {
        String quoted = address.replaceAll("([^0-9a-zA-Z])", "\\\\$1");
        return "(?<![0-9a-fA-F:.])" + quoted + "(?![0-9a-fA-F:]|\\.[0-9])";
    }

    public UUID uuid() {
        return uuid;
    }

    public String contains() {
        return "%" + escapeLike(lower) + "%";
    }

    public String startsWith() {
        return escapeLike(lower) + "%";
    }

    public String macStartsWith() {
        return escapeLike(macPrefix) + "%";
    }

    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public String cacheKey() {
        return lower;
    }

}
