package app.nzyme.core.ethernet.icmp;

public class IcmpType {

    public static String name(int ipVersion, int type) {
        return ipVersion == 6 ? nameV6(type) : nameV4(type);
    }

    private static String nameV4(int type) {
        return switch (type) {
            case 0 -> "Echo Reply";
            case 3 -> "Destination Unreachable";
            case 4 -> "Source Quench";
            case 5 -> "Redirect";
            case 8 -> "Echo Request";
            case 9 -> "Router Advertisement";
            case 10 -> "Router Solicitation";
            case 11 -> "Time Exceeded";
            case 12 -> "Parameter Problem";
            case 13 -> "Timestamp Request";
            case 14 -> "Timestamp Reply";
            case 15 -> "Information Request";
            case 16 -> "Information Reply";
            case 17 -> "Address Mask Request";
            case 18 -> "Address Mask Reply";
            default -> "Type " + type;
        };
    }

    private static String nameV6(int type) {
        return switch (type) {
            case 1 -> "Destination Unreachable";
            case 2 -> "Packet Too Big";
            case 3 -> "Time Exceeded";
            case 4 -> "Parameter Problem";
            case 128 -> "Echo Request";
            case 129 -> "Echo Reply";
            case 137 -> "Redirect";
            case 138 -> "Router Renumbering";
            case 139 -> "Node Information Query";
            case 140 -> "Node Information Response";
            case 141 -> "Inverse Neighbor Solicitation";
            case 142 -> "Inverse Neighbor Advertisement";
            default -> "Type " + type;
        };
    }

    public static boolean isError(int ipVersion, int type) {
        if (ipVersion == 6) {
            return (type >= 1 && type <= 4) || type == 137;
        }

        return type == 3 || type == 4 || type == 5 || type == 11 || type == 12;
    }

}
