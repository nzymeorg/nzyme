package app.nzyme.core.dot11.monitoring.ssids;

import java.util.List;
import java.util.UUID;

// What taps recorded of an SSID or BSSID: as a whole, and at each location. Taps without a location count as one.
public record Sightings(String name, int activeMinutes, List<UUID> taps, List<Location> perLocation) {

    public record Location(int activeMinutes, List<UUID> taps) { }

    // Alerted where it stayed for the dwell time, or where it stayed longest if it did so nowhere.
    public List<UUID> tapsToAlertAt(int dwellTimeMinutes) {
        int longest = perLocation.stream().mapToInt(Location::activeMinutes).max().orElse(0);
        List<UUID> alertAt = perLocation.stream()
                .filter(location -> location.activeMinutes() >= Math.min(dwellTimeMinutes, longest))
                .flatMap(location -> location.taps().stream())
                .toList();

        return alertAt.isEmpty() ? taps : alertAt;
    }

}
