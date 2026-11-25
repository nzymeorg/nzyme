package app.nzyme.core.dot11;

import app.nzyme.core.dot11.monitoring.MultipleChannelsRule;
import app.nzyme.core.dot11.monitoring.MultipleChannelsRule.Finding;
import app.nzyme.core.dot11.monitoring.MultipleChannelsRule.Sighting;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;

public class MultipleChannelsRuleTest {

    private static final DateTime START = new DateTime(2026, 11, 25, 12, 0, 0);

    private static void seen(List<Sighting> sightings, int frequency, int fromSecond, int untilSecond, int everySeconds) {
        for (int second = fromSecond; second <= untilSecond; second += everySeconds) {
            sightings.add(new Sighting(START.plusSeconds(second), frequency));
        }
    }

    @Test
    public void testOneChannelIsNoFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2437, 0, 600, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.empty());
        assertEquals(MultipleChannelsRule.evaluate(List.of()), Optional.empty());
    }

    @Test
    public void testChannelChangeIsNoFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2437, 0, 300, 10);
        seen(sightings, 2462, 300, 600, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.empty());
    }

    @Test
    public void testChannelChangeWithLateReportsIsNoFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2437, 0, 320, 10);
        seen(sightings, 2462, 300, 600, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.empty());
    }

    @Test
    public void testChangeAndReturnIsNoFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2437, 0, 200, 10);
        seen(sightings, 2462, 200, 420, 10);
        seen(sightings, 2437, 400, 600, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.empty());
    }

    @Test
    public void testBothChannelsInEveryReportIsAFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2462, 0, 90, 10);
        seen(sightings, 2437, 0, 90, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.of(new Finding(2437, 2462)));
    }

    @Test
    public void testBothChannelsForLessThanThreeChangesIsNoFinding() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2462, 0, 80, 10);
        seen(sightings, 2437, 0, 80, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.empty());
    }

    @Test
    public void testHoppingTapSeesBackAndForth() {
        List<Sighting> fast = Lists.newArrayList();
        seen(fast, 2437, 0, 200, 25);
        seen(fast, 2462, 5, 200, 25);
        assertEquals(MultipleChannelsRule.evaluate(fast), Optional.of(new Finding(2437, 2462)));

        List<Sighting> slow = Lists.newArrayList();
        seen(slow, 5180, 0, 300, 142);
        seen(slow, 5745, 30, 300, 142);
        Collections.shuffle(slow);
        assertEquals(MultipleChannelsRule.evaluate(slow), Optional.of(new Finding(5180, 5745)));

        List<Sighting> twoVisits = Lists.newArrayList();
        seen(twoVisits, 5180, 0, 150, 142);
        seen(twoVisits, 5745, 30, 100, 142);
        assertEquals(MultipleChannelsRule.evaluate(twoVisits), Optional.empty());
    }

    @Test
    public void testOnlyTheMostSightedChannelsAreTried() {
        List<Sighting> sightings = Lists.newArrayList();
        for (int frequency = 5000; frequency < 5200; frequency++) {
            seen(sightings, frequency, 0, 0, 10);
        }
        seen(sightings, 2437, 0, 200, 10);
        seen(sightings, 2462, 0, 200, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.of(new Finding(2437, 2462)));
    }

    @Test
    public void testFindsThePairAmongSeveralChannels() {
        List<Sighting> sightings = Lists.newArrayList();
        seen(sightings, 2412, 0, 0, 10);
        seen(sightings, 2437, 0, 200, 10);
        seen(sightings, 2462, 0, 200, 10);

        assertEquals(MultipleChannelsRule.evaluate(sightings), Optional.of(new Finding(2437, 2462)));
    }

}
