package app.nzyme.core.monitors;

import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.plugin.Subsystem;

import java.util.Arrays;
import java.util.List;

public enum MonitorType {

    DOT11_BSSID(Subsystem.DOT11),
    DOT11_CLIENT_CONNECTED(Subsystem.DOT11),
    DOT11_CLIENT_DISCONNECTED(Subsystem.DOT11),

    BLUETOOTH_DEVICE(Subsystem.BLUETOOTH),

    ETHERNET_ASSET(Subsystem.ETHERNET),
    ETHERNET_L4_SESSION(Subsystem.ETHERNET),
    ETHERNET_DNS_TRANSACTION(Subsystem.ETHERNET),
    ETHERNET_DHCP_TRANSACTION(Subsystem.ETHERNET),
    ETHERNET_ARP_PACKET(Subsystem.ETHERNET),
    ETHERNET_SSH_SESSION(Subsystem.ETHERNET),
    ETHERNET_SOCKS_TUNNEL(Subsystem.ETHERNET),
    ETHERNET_VPN_TUNNEL(Subsystem.ETHERNET),
    ETHERNET_TLS_SESSION(Subsystem.ETHERNET),
    ETHERNET_TLS_CERTIFICATE(Subsystem.ETHERNET),
    ETHERNET_RTSP_STREAM(Subsystem.ETHERNET),
    ETHERNET_WEBRTC_SESSION(Subsystem.ETHERNET),
    ETHERNET_NTP_TRANSACTION(Subsystem.ETHERNET),
    ETHERNET_STUN_DISCOVERY(Subsystem.ETHERNET),
    ETHERNET_STUN_CONNECTION(Subsystem.ETHERNET),
    ETHERNET_PORTAL_INTEGRITY_REPORT(Subsystem.ETHERNET);

    private final Subsystem subsystem;

    MonitorType(Subsystem subsystem) {
        this.subsystem = subsystem;
    }

    public Subsystem getSubsystem() {
        return subsystem;
    }

    public DetectionType getDetectionType() {
        return switch (subsystem) {
            case DOT11 -> DetectionType.MONITOR_TRIGGERED_DOT11;
            case BLUETOOTH -> DetectionType.MONITOR_TRIGGERED_BLUETOOTH;
            case ETHERNET -> DetectionType.MONITOR_TRIGGERED_ETHERNET;
            default -> throw new IllegalStateException("No monitor detection type for subsystem [" + subsystem + "].");
        };
    }

    public String getRequiredManagePermission() {
        return switch (subsystem) {
            case DOT11 -> "dot11_monitoring_manage";
            case BLUETOOTH -> "bluetooth_monitoring_manage";
            case ETHERNET -> "ethernet_monitoring_manage";
            default -> throw new IllegalStateException("No monitor manage permission for subsystem [" + subsystem + "].");
        };
    }

    public static List<MonitorType> ofSubsystem(Subsystem subsystem) {
        return Arrays.stream(values())
                .filter(t -> t.subsystem.equals(subsystem))
                .toList();
    }

}
