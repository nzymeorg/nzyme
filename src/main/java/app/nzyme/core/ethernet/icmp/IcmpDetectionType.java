package app.nzyme.core.ethernet.icmp;

public enum IcmpDetectionType {

    REDIRECT("Redirect"),
    TRACEROUTE("Traceroute"),
    TUNNEL("Tunnel");

    private final String humanReadable;

    IcmpDetectionType(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
