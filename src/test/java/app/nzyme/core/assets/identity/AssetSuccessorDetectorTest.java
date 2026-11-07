package app.nzyme.core.assets.identity;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class AssetSuccessorDetectorTest {

    private static AssetIdentityConfiguration config(int lookbackHours) {
        return AssetIdentityConfiguration.create(true, 15, 3, true, true, true, 24, 24, 50, true,
                true, lookbackHours, 30, 50, false, 365);
    }

    @Test
    public void testNextCheckFollowsScheduleAfterFirstCheck() {
        DateTime firstSeen = new DateTime(2026, 11, 6, 10, 0);
        DateTime now = firstSeen.plusMinutes(31);

        assertEquals(firstSeen.plusHours(6), AssetSuccessorDetector.nextCheck(firstSeen, now, config(72)));
    }

    @Test
    public void testNextCheckSkipsAgesAlreadyPassed() {
        DateTime firstSeen = new DateTime(2026, 11, 6, 10, 0);
        DateTime now = firstSeen.plusHours(30);

        assertEquals(firstSeen.plusHours(72), AssetSuccessorDetector.nextCheck(firstSeen, now, config(72)));
    }

    @Test
    public void testNextCheckEndsWithLookback() {
        DateTime firstSeen = new DateTime(2026, 11, 6, 10, 0);

        assertNull(AssetSuccessorDetector.nextCheck(firstSeen, firstSeen.plusHours(72), config(72)));
        assertNull(AssetSuccessorDetector.nextCheck(firstSeen, firstSeen.plusHours(73), config(72)));
    }

    @Test
    public void testNextCheckRespectsShortLookback() {
        DateTime firstSeen = new DateTime(2026, 11, 6, 10, 0);
        DateTime now = firstSeen.plusMinutes(31);

        assertEquals(firstSeen.plusHours(2), AssetSuccessorDetector.nextCheck(firstSeen, now, config(2)));
        assertNull(AssetSuccessorDetector.nextCheck(firstSeen, firstSeen.plusHours(2), config(2)));
    }

}
