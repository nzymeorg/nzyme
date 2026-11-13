package app.nzyme.core.taps;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CaptureTest {

    private static Capture capture(long droppedBuffer, long droppedInterface) {
        return Capture.create(UUID.randomUUID(), "eth0", "Ethernet", true, 5000L, droppedBuffer, droppedInterface,
                0, DateTime.now(), DateTime.now());
    }

    @Test
    public void testIsDroppingAboveThresholdOnEitherCounter() {
        assertTrue(capture(Capture.DROPPING_THRESHOLD + 1, 0).isDropping());
        assertTrue(capture(0, Capture.DROPPING_THRESHOLD + 1).isDropping());
    }

    @Test
    public void testIsNotDroppingUpToThreshold() {
        assertFalse(capture(0, 0).isDropping());
        assertFalse(capture(Capture.DROPPING_THRESHOLD, Capture.DROPPING_THRESHOLD).isDropping());
    }

}
