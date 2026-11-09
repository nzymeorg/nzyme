package app.nzyme.core.database;

import app.nzyme.plugin.Subsystem;

public enum DataCategory {

    DOT11(Subsystem.DOT11),
    BLUETOOTH(Subsystem.BLUETOOTH),
    ETHERNET_L4(Subsystem.ETHERNET),
    ETHERNET_DNS(Subsystem.ETHERNET),
    PORTAL_INTEGRITY(Subsystem.ETHERNET),
    TLS_CERTIFICATES(Subsystem.ETHERNET),
    UAV(Subsystem.UAV),
    TIMELINE_EVENTS_DOT11(Subsystem.DOT11),
    ASSET_ACTIVITY(Subsystem.ETHERNET),
    ASSET_ATTRIBUTES(Subsystem.ETHERNET),
    ASSET_COMMUNICATION(Subsystem.ETHERNET),
    ASSET_STATISTICS(Subsystem.ETHERNET),
    ASSET_IDENTITY(Subsystem.ETHERNET);

    private final Subsystem subsystem;

    DataCategory(Subsystem subsystem) {
        this.subsystem = subsystem;
    }

    public Subsystem getSubsystem() {
        return subsystem;
    }

}
