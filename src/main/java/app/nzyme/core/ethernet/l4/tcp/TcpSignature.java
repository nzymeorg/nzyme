package app.nzyme.core.ethernet.l4.tcp;

import com.google.common.hash.Hashing;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * TCP stack signature format. Each form is a prefix of the next:
 *   stack        ittl:df:ecn:options:quirks   (operating system)
 *   fingerprint  stack:mss:wscale             (link)
 *   signature    fingerprint:window:tos       (application)
 * Quirks: ts0 zero timestamp value, ecr non-zero timestamp echo in the opening segment, id0 zero IP ID
 * with DF set, bad options could not be read to the end.
 *
 * The option layout leaves out TCP Fast Open (kind 34 and the experimental kind 254), Multipath TCP (kind 30) and
 * the padding after the last real option. A stack only adds them on some connections, which would otherwise make
 * one device show up as two stacks.
 */
public class TcpSignature {

    private static final int[] INITIAL_TTLS = {32, 64, 128, 255};

    private static final Set<Integer> IGNORED_OPTIONS = Set.of(30, 34, 254);

    private static final int STACK_FIELDS = 5;
    private static final int ECN_FIELD = 2;
    private static final int OPTIONS_FIELD = 3;
    private static final int QUIRKS_FIELD = 4;

    private static final Map<Integer, String> OPTION_NAMES = Map.of(
            0, "eol",
            1, "nop",
            2, "mss",
            3, "ws",
            4, "sok",
            5, "sack",
            8, "ts"
    );

    private final int initialTtl;
    private final int hopDistance;
    private final boolean df;
    private final int mss;
    @Nullable
    private final Integer windowScale;
    private final boolean ecn;
    private final List<Integer> options;
    private final List<String> quirks;
    private final int windowSize;
    private final int tos;

    public TcpSignature(int ttl,
                        int tos,
                        boolean df,
                        int ipId,
                        boolean cwr,
                        boolean ece,
                        int windowSize,
                        @Nullable Integer mss,
                        @Nullable Integer windowScale,
                        @Nullable List<Integer> options,
                        boolean optionsTruncated,
                        @Nullable Long timestampValue,
                        @Nullable Long timestampEcho) {
        this.initialTtl = guessInitialTtl(ttl);
        this.hopDistance = this.initialTtl - ttl;
        this.df = df;
        this.mss = mss == null ? 0 : mss;
        this.windowScale = windowScale;
        this.ecn = cwr && ece;
        this.options = normalizeOptions(options);
        this.windowSize = windowSize;
        this.tos = tos;

        List<String> q = new ArrayList<>();
        if (timestampValue != null && timestampValue == 0) {
            q.add("ts0");
        }
        if (timestampEcho != null && timestampEcho != 0) {
            q.add("ecr");
        }
        if (df && ipId == 0) {
            q.add("id0");
        }
        if (optionsTruncated) {
            q.add("bad");
        }
        this.quirks = q;
    }

    // Stack layer with ECN neutralized, because Apple decides it per connection, and with kind 30 removed from
    // values recorded before it was left out.
    public static String comparisonKey(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length < STACK_FIELDS) {
            return value;
        }

        parts[ECN_FIELD] = "-";

        if (!parts[OPTIONS_FIELD].isEmpty()) {
            List<String> options = new ArrayList<>(List.of(parts[OPTIONS_FIELD].split(",")));
            if (options.remove("30")) {
                while (!options.isEmpty() && (options.get(options.size() - 1).equals("nop")
                        || options.get(options.size() - 1).equals("eol"))) {
                    options.remove(options.size() - 1);
                }
            }
            parts[OPTIONS_FIELD] = String.join(",", options);
        }

        return String.join(":", Arrays.copyOfRange(parts, 0, STACK_FIELDS));
    }

    public static boolean hasTimestamps(String value) {
        String[] parts = value.split(":", -1);

        return parts.length >= STACK_FIELDS && List.of(parts[OPTIONS_FIELD].split(",")).contains("ts");
    }

    public static String timestamplessKey(String value) {
        String[] parts = comparisonKey(value).split(":", -1);
        if (parts.length < STACK_FIELDS) {
            return value;
        }

        List<String> options = new ArrayList<>(List.of(parts[OPTIONS_FIELD].split(",")));
        options.removeAll(Set.of("ts", "nop", "eol"));
        parts[OPTIONS_FIELD] = String.join(",", options);

        List<String> quirks = new ArrayList<>(List.of(parts[QUIRKS_FIELD].split(",")));
        quirks.removeAll(Set.of("ts0", "ecr", "-"));
        parts[QUIRKS_FIELD] = quirks.isEmpty() ? "-" : String.join(",", quirks);

        return String.join(":", parts);
    }

    // Windows applications can turn timestamps on per socket, which also moves the padding. Two stacks that both
    // send timestamps still have to match exactly.
    public static boolean sameStack(String a, String b) {
        return comparisonKey(a).equals(comparisonKey(b))
                || (hasTimestamps(a) != hasTimestamps(b) && timestamplessKey(a).equals(timestamplessKey(b)));
    }

    public static int guessInitialTtl(int ttl) {
        for (int candidate : INITIAL_TTLS) {
            if (ttl <= candidate) {
                return candidate;
            }
        }

        return 255;
    }

    private static List<Integer> normalizeOptions(@Nullable List<Integer> options) {
        if (options == null) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        for (Integer kind : options) {
            if (!IGNORED_OPTIONS.contains(kind)) {
                result.add(kind);
            }
        }

        while (!result.isEmpty() && (result.get(result.size() - 1) == 0 || result.get(result.size() - 1) == 1)) {
            result.remove(result.size() - 1);
        }

        return result;
    }

    public int initialTtl() {
        return initialTtl;
    }

    public int hopDistance() {
        return hopDistance;
    }

    public boolean isNative() {
        return hopDistance == 0;
    }

    public String optionLayout() {
        List<String> names = new ArrayList<>();
        for (Integer kind : options) {
            names.add(OPTION_NAMES.getOrDefault(kind, String.valueOf(kind)));
        }

        return String.join(",", names);
    }

    public String windowClass() {
        if (windowSize == 65535) {
            return "max";
        }

        if (mss > 0 && windowSize > 0 && windowSize % mss == 0) {
            return "mss*" + (windowSize / mss);
        }

        return String.valueOf(windowSize);
    }

    public String stack() {
        return initialTtl + ":" + (df ? "1" : "0") + ":" + (ecn ? "1" : "0") + ":" + optionLayout() + ":"
                + (quirks.isEmpty() ? "-" : String.join(",", quirks));
    }

    public String fingerprint() {
        return stack() + ":" + mss + ":" + (windowScale == null ? "-" : windowScale);
    }

    public String signature() {
        return fingerprint() + ":" + windowClass() + ":" + tos;
    }

    public static String sourcePortRange(int port) {
        if (port < 1024) {
            return "0-1023";
        }
        if (port < 32768) {
            return "1024-32767";
        }
        if (port < 49152) {
            return "32768-49151";
        }
        if (port < 61000) {
            return "49152-60999";
        }
        return "61000-65535";
    }

    @Nullable
    public static Integer timestampRate(@Nullable Long firstValue, @Nullable org.joda.time.DateTime firstTime,
                                        @Nullable Long lastValue, @Nullable org.joda.time.DateTime lastTime) {
        if (firstValue == null || lastValue == null || firstTime == null || lastTime == null) {
            return null;
        }

        long elapsedMillis = lastTime.getMillis() - firstTime.getMillis();
        if (elapsedMillis < 5000) {
            return null;
        }

        long ticks = (lastValue - firstValue) & 0xFFFFFFFFL;
        if (ticks == 0 || ticks > 0x7FFFFFFFL) {
            return null;
        }

        return (int) Math.round(ticks * 1000.0 / elapsedMillis);
    }

    @Nullable
    public static String timestampRateBucket(@Nullable Integer rate) {
        if (rate == null || rate <= 0) {
            return null;
        }

        for (int nominal : new int[]{1, 2, 10, 100, 128, 200, 250, 500, 1000}) {
            if (Math.abs(rate - nominal) <= Math.max(1, nominal * 0.15)) {
                return String.valueOf(nominal);
            }
        }

        return "other";
    }

    @Nullable
    public static String ipIdBehavior(int synIpId, @Nullable Integer latestIpId, long clientSegments) {
        if (latestIpId == null || clientSegments < 4) {
            return null;
        }

        if (synIpId == 0 && latestIpId == 0) {
            return "ZERO";
        }

        long delta = (latestIpId - synIpId) & 0xFFFF;
        long expected = clientSegments - 1;

        if (delta >= expected && delta <= expected * 2) {
            return "SEQUENTIAL";
        }

        return "OTHER";
    }

    @Nullable
    public static String shortId(@Nullable String value) {
        if (value == null) {
            return null;
        }

        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString().substring(0, 8);
    }

}
