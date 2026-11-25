package app.nzyme.core.dot11.monitoring;

import org.joda.time.DateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/*
 * An access point that changes its channel is on one channel and then on the other. A second transmitter with the
 * same BSSID keeps the BSSID on both, so a tap sees it go back and forth between them.
 */
public final class MultipleChannelsRule {

    public static final int WINDOW_MINUTES = 10;
    public static final int MINIMUM_BEACONS_PER_REPORT = 2;

    // Frames around a real change can reach the node up to two reports apart, which must not count as going back.
    public static final int MINIMUM_SECONDS_BETWEEN_CHANGES = 30;

    public static final int MINIMUM_CHANGES = 3;
    public static final int MAXIMUM_FREQUENCIES = 8;

    public record Sighting(DateTime at, int frequency) {}

    public record Finding(int firstFrequency, int secondFrequency) {}

    private MultipleChannelsRule() {}

    // Sightings of one BSSID by one tap. Reports of several taps reach the node with different delays.
    public static Optional<Finding> evaluate(List<Sighting> sightings) {
        List<Sighting> ordered = sightings.stream()
                .sorted(Comparator.comparing(Sighting::at))
                .toList();

        // A tap decides which frequencies it reports, and every pair of them is tried.
        Set<Integer> frequencies = ordered.stream()
                .collect(Collectors.groupingBy(Sighting::frequency, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAXIMUM_FREQUENCIES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        for (int first : frequencies) {
            for (int second : frequencies) {
                if (first < second && (changes(ordered, first, second) >= MINIMUM_CHANGES
                        || changes(ordered, second, first) >= MINIMUM_CHANGES)) {
                    return Optional.of(new Finding(first, second));
                }
            }
        }

        return Optional.empty();
    }

    private static int changes(List<Sighting> ordered, int startsOn, int other) {
        int expected = startsOn;
        DateTime previous = null;
        int seen = 0;

        for (Sighting sighting : ordered) {
            if (sighting.frequency() != expected) {
                continue;
            }

            if (previous != null && sighting.at().isBefore(previous.plusSeconds(MINIMUM_SECONDS_BETWEEN_CHANGES))) {
                continue;
            }

            seen++;
            previous = sighting.at();
            expected = expected == startsOn ? other : startsOn;
        }

        return Math.max(0, seen - 1);
    }

}
