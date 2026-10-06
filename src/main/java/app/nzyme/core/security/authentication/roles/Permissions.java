package app.nzyme.core.security.authentication.roles;

import java.util.Map;
import java.util.TreeMap;

public class Permissions {

    public static final Map<String, Permission> ALL = new TreeMap<>(){{
        put("alerts_manage", Permission.create(
                "alerts_manage",
                "Manage Alerts",
                "Allows user to create, edit and delete alert definitions across data from all taps of tenant " +
                        "the user belongs to. This includes classifying UAVs.",
                false
        ));
        put("alerts_view", Permission.create(
                "alerts_view",
                "View/Handle Alerts",
                "Allows user to view triggered alerts across data from all taps of tenant the user belongs to.",
                false
        ));
        put("dot11_monitoring_manage", Permission.create(
                "dot11_monitoring_manage",
                "Manage WiFi Monitoring",
                "Allows user to create, edit and delete configurations of WiFi monitoring. This can " +
                        "potentially trigger alerts based on data from all taps of tenant the user belongs to.",
                false
        ));
        put("mac_context_manage", Permission.create(
                "mac_context_manage",
                "Manage MAC Address Context",
                "Allows user to create, edit and delete MAC address context for all subsystems, " +
                        "like 802.11/WiFi or Ethernet.",
                true
        ));
        put("network_context_manage", Permission.create(
                "network_context_manage",
                "Manage Ethernet Network Context",
                "Allows user to create, edit and delete Ethernet network (CIDR) context.",
                true
        ));
        put("uav_monitoring_manage", Permission.create(
                "uav_monitoring_manage",
                "Manage UAV Monitoring",
                "Allows user to create, edit and delete configurations of UAV monitoring, including UAV " +
                        "classifications and types. This can potentially trigger alerts based on data from all taps " +
                        "of tenant the user belongs to.",
                false
        ));
        put("bluetooth_monitoring_manage", Permission.create(
                "bluetooth_monitoring_manage",
                "Manage Bluetooth Monitoring",
                "Allows user to create, edit and delete configurations of Bluetooth monitoring, This can " +
                        "potentially trigger alerts based on data from all taps of tenant the user belongs to.",
                false
        ));
        put("ethernet_assets_manage", Permission.create(
                "ethernet_assets_manage",
                "Manage Ethernet Assets",
                "Allows user to create, edit and delete configurations related to Ethernet asset management.",
                false
        ));
        put("api_keys_manage_own", Permission.create(
                "api_keys_manage_own",
                "Manage own API Keys",
                "Allows user to manage their own API keys. Note that removing this permissions does not delete existing API keys.",
                true
        ));
    }};

}
