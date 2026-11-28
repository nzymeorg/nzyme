package app.nzyme.core.ethernet.portalintegrity;

import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PortalUrls {

    public static final int MAX_URL_LENGTH = 2048;

    private static final Pattern CERTIFICATE_FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HOST = Pattern.compile("[a-z0-9._-]+|\\[[0-9a-f:.]+]");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    private static final Pattern UNPRINTABLE = Pattern.compile("[\\p{Cntrl}\\s]");

    private record Parts(String scheme, String host, @Nullable String port, String path) {}

    private PortalUrls() {}

    /*
     * Scheme, host, port and path. Portals put session tokens into the query string, so two requests rarely
     * end at the same full URL.
     */
    public static String withoutQuery(String url) {
        // Whitespace and control characters have no place in a URL and would make it differ from what a user types.
        String trimmed = UNPRINTABLE.matcher(url).replaceAll("");

        Optional<Parts> parts = parse(trimmed);
        if (parts.isEmpty()) {
            return limit(cutAt(trimmed, "?#"));
        }

        Parts p = parts.get();
        return limit(p.scheme() + "://" + p.host() + (p.port() == null ? "" : ":" + p.port()) + p.path());
    }

    public static Optional<String> hostOf(String url) {
        return parse(url.trim()).map(Parts::host);
    }

    /*
     * A URL as a probe reports it: no whitespace or control characters, an ASCII host (taps report punycode) and
     * a numeric port.
     */
    public static boolean isHttpUrl(String url) {
        String trimmed = url.trim();
        if (UNPRINTABLE.matcher(trimmed).find()) {
            return false;
        }

        Optional<Parts> parts = parse(trimmed);

        return parts.isPresent()
                && (parts.get().scheme().equals("http") || parts.get().scheme().equals("https"))
                && HOST.matcher(parts.get().host()).matches()
                && (parts.get().port() == null || PORT.matcher(parts.get().port()).matches());
    }

    public static boolean sameSite(String host, String otherHost) {
        return withoutWww(host).equals(withoutWww(otherHost));
    }

    public static Optional<String> normalizeAddress(@Nullable String address) {
        // An address with a zone, like fe80::1%eth0, is only valid on the host it came from.
        if (address == null || address.contains("%") || !InetAddresses.isInetAddress(address.trim())) {
            return Optional.empty();
        }

        return Optional.of(InetAddresses.toAddrString(InetAddresses.forString(address.trim())));
    }

    // SHA-256 fingerprint in lowercase hex, also accepted with colons or spaces between the bytes.
    public static Optional<String> normalizeCertificate(@Nullable String fingerprint) {
        if (fingerprint == null) {
            return Optional.empty();
        }

        String normalized = fingerprint.replaceAll("[:\\s]", "").toLowerCase(Locale.ROOT);

        return CERTIFICATE_FINGERPRINT.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }

    private static Optional<Parts> parse(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0) {
            return Optional.empty();
        }

        String scheme = url.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        String rest = url.substring(schemeEnd + 3);

        String authority = cutAt(rest, "/?#");
        String path = cutAt(rest.substring(authority.length()), "?#");

        int userInfoEnd = authority.lastIndexOf('@');
        if (userInfoEnd >= 0) {
            authority = authority.substring(userInfoEnd + 1);
        }
        authority = authority.toLowerCase(Locale.ROOT);

        String host = authority;
        String port = null;
        int portStart = authority.lastIndexOf(':');
        if (portStart >= 0 && portStart > authority.lastIndexOf(']')) {
            host = authority.substring(0, portStart);
            port = authority.substring(portStart + 1);
        }

        if (port != null && (port.isEmpty()
                || (scheme.equals("http") && port.equals("80")) || (scheme.equals("https") && port.equals("443")))) {
            port = null;
        }

        return Optional.of(new Parts(scheme, host, port, path.isEmpty() ? "/" : path));
    }

    private static String withoutWww(String host) {
        String lower = host.toLowerCase(Locale.ROOT);

        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    private static String cutAt(String value, String characters) {
        for (int i = 0; i < value.length(); i++) {
            if (characters.indexOf(value.charAt(i)) >= 0) {
                return value.substring(0, i);
            }
        }

        return value;
    }

    private static String limit(String value) {
        return value.length() > MAX_URL_LENGTH ? value.substring(0, MAX_URL_LENGTH) : value;
    }

}
