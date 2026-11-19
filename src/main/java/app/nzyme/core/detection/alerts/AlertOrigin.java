package app.nzyme.core.detection.alerts;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// An alert is raised once per location of the taps that observed it, at an explicit location, or at no location.
public final class AlertOrigin {

    private final List<UUID> taps;
    private final List<UUID> knownTaps;
    @Nullable
    private final UUID locationId;

    private AlertOrigin(List<UUID> taps, List<UUID> knownTaps, @Nullable UUID locationId) {
        this.taps = taps;
        this.knownTaps = knownTaps;
        this.locationId = locationId;
    }

    public static AlertOrigin tap(UUID tap) {
        return new AlertOrigin(ImmutableList.of(tap), ImmutableList.of(), null);
    }

    // Taps that are unknown or have no location, and an empty collection, place the alert at no location.
    public static AlertOrigin taps(Collection<UUID> taps) {
        return new AlertOrigin(ImmutableList.copyOf(taps), ImmutableList.of(), null);
    }

    public static AlertOrigin location(@Nullable UUID locationId) {
        return new AlertOrigin(ImmutableList.of(), ImmutableList.of(), locationId);
    }

    public static AlertOrigin noLocation() {
        return location(null);
    }

    // For something that was alerted on before and is now seen by more taps: skips the locations of the earlier taps.
    public AlertOrigin excludingLocationsOf(Collection<UUID> knownTaps) {
        return new AlertOrigin(taps, ImmutableList.copyOf(knownTaps), locationId);
    }

    // Nothing with unknown taps is seen elsewhere: it is from before its taps were recorded and was alerted on.
    public static boolean seenElsewhere(Collection<UUID> knownTaps, Collection<UUID> taps) {
        return !knownTaps.isEmpty() && !knownTaps.containsAll(taps);
    }

    List<UUID> taps() {
        return taps;
    }

    List<UUID> knownTaps() {
        return knownTaps;
    }

    @Nullable
    UUID locationId() {
        return locationId;
    }

}
