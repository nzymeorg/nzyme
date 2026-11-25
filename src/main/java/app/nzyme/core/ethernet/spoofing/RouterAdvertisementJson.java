package app.nzyme.core.ethernet.spoofing;

import app.nzyme.core.ethernet.CIDR;
import app.nzyme.core.ethernet.ndp.NdpPrefix;
import app.nzyme.core.ethernet.segmentation.MembershipResolver;
import com.google.common.net.InetAddresses;
import jakarta.annotation.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

public final class RouterAdvertisementJson {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final int MAX_DOMAIN_LENGTH = 253;

    private RouterAdvertisementJson() { }

    public static List<String> prefixes(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return OM.readValue(json, new TypeReference<List<NdpPrefix>>() { }).stream()
                    .map(p -> network(p.prefix(), p.prefixLength()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
        } catch (JacksonException e) {
            return List.of();
        }
    }

    public static List<String> addresses(@Nullable String json) {
        return strings(json).stream()
                .filter(InetAddresses::isInetAddress)
                .map(a -> InetAddresses.toAddrString(InetAddresses.forString(a)))
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> domains(@Nullable String json) {
        return strings(json).stream()
                .filter(d -> !d.isEmpty() && d.length() <= MAX_DOMAIN_LENGTH
                        && d.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_'))
                .map(String::toLowerCase)
                .distinct()
                .sorted()
                .toList();
    }

    // A router may advertise a prefix with bits set beyond its length. Hosts ignore them.
    @Nullable
    static String network(@Nullable String prefix, int length) {
        if (prefix == null || length < 0 || length > 128 || !InetAddresses.isInetAddress(prefix)) {
            return null;
        }

        InetAddress address = InetAddresses.forString(prefix);
        if (!(address instanceof Inet6Address)) {
            return null;
        }

        try {
            return new CIDR(InetAddress.getByAddress(MembershipResolver.mask(address.getAddress(), length)), length)
                    .toString();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static List<String> strings(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return OM.readValue(json, new TypeReference<List<String>>() { }).stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (JacksonException e) {
            return List.of();
        }
    }

}
