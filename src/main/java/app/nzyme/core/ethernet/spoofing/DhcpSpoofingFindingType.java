package app.nzyme.core.ethernet.spoofing;

public enum DhcpSpoofingFindingType {

    ROGUE_SERVER("Rogue DHCP server"),

    PARAMETERS_CHANGED("DHCP configuration changed");

    private final String humanReadable;

    DhcpSpoofingFindingType(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
