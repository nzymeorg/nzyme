package app.nzyme.core.ethernet.icmp;

// The type numbers of ICMP and ICMPv6 overlap (3 is Destination Unreachable in ICMP and Time Exceeded in ICMPv6),
// so a type is never compared without the version. The prefix is the table alias with its dot, or empty.
public final class IcmpSql {

    private IcmpSql() { }

    public static String echoRequest(String prefix) {
        return either(prefix, "@icmp_type = 8", "@icmp_type = 128");
    }

    public static String echoReply(String prefix) {
        return either(prefix, "@icmp_type = 0", "@icmp_type = 129");
    }

    public static String unreachable(String prefix) {
        return either(prefix, "@icmp_type = 3", "@icmp_type = 1");
    }

    public static String portUnreachable(String prefix) {
        return either(prefix, "@icmp_type = 3 AND @icmp_code = 3", "@icmp_type = 1 AND @icmp_code = 4");
    }

    // No route to the network or the host: codes 0 and 1 in ICMP, 0 and 3 in ICMPv6.
    public static String hostUnreachable(String prefix) {
        return either(prefix, "@icmp_type = 3 AND @icmp_code IN (0, 1)", "@icmp_type = 1 AND @icmp_code IN (0, 3)");
    }

    // The code of a hostUnreachable() row in ICMP numbering, so both versions share the reasons 0 and 1.
    public static String hostUnreachableCode(String prefix) {
        return ("(CASE WHEN @ip_version = 6 AND @icmp_code = 3 THEN 1 ELSE @icmp_code END)").replace("@", prefix);
    }

    public static String timeExceeded(String prefix) {
        return either(prefix, "@icmp_type = 11", "@icmp_type = 3");
    }

    public static String redirect(String prefix) {
        return either(prefix, "@icmp_type = 5", "@icmp_type = 137");
    }

    public static String sweepRequest(String prefix) {
        return either(prefix, "@icmp_type IN (8, 13, 17)", "@icmp_type = 128");
    }

    public static String sweepReply(String prefix) {
        return either(prefix, "@icmp_type IN (0, 14, 18)", "@icmp_type = 129");
    }

    // One value per type of either version: the number for ICMP, like "8", and "6:128" for ICMPv6.
    public static String typeKey(String prefix) {
        return ("(CASE WHEN @ip_version = 6 THEN '6:' || @icmp_type ELSE @icmp_type::text END)").replace("@", prefix);
    }

    public static String typeKey(int ipVersion, int icmpType) {
        return ipVersion == 6 ? "6:" + icmpType : String.valueOf(icmpType);
    }

    private static String either(String prefix, String icmp, String icmpv6) {
        return ("((@ip_version = 4 AND " + icmp + ") OR (@ip_version = 6 AND " + icmpv6 + "))").replace("@", prefix);
    }

}
