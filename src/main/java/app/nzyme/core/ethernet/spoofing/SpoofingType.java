package app.nzyme.core.ethernet.spoofing;

public enum SpoofingType {

    ARP("ARP Poisoning");

    private final String humanReadable;

    SpoofingType(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

}
