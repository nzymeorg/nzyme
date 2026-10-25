package app.nzyme.core.ethernet.icmp;

public class IcmpType {

    public static String name(int type) {
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

    public static boolean isError(int type) {
        return type == 3 || type == 4 || type == 5 || type == 11 || type == 12;
    }

}
