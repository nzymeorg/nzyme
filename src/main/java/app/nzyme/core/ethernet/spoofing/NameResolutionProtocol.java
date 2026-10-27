package app.nzyme.core.ethernet.spoofing;

public enum NameResolutionProtocol {

    LLMNR("LLMNR"),
    MDNS("mDNS"),
    NBTNS("NBT-NS");

    private final String humanReadable;

    NameResolutionProtocol(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
