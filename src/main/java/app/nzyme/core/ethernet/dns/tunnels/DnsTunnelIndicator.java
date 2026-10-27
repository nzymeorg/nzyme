package app.nzyme.core.ethernet.dns.tunnels;

public enum DnsTunnelIndicator {

    HIGH_ENTROPY("High entropy"),
    LONG_NAMES("Long names"),
    UNUSUAL_RECORD_TYPES("Unusual record types");

    private final String humanReadable;

    DnsTunnelIndicator(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
