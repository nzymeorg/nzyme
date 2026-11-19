package app.nzyme.core.monitors;

import app.nzyme.core.monitors.db.MonitorEntry;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.*;

public class MonitorsThreadTest {

    private MonitorEntry monitor(MonitorStatus status, DateTime statusChangedAt, int interval) {
        return MonitorEntry.create(
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                true,
                MonitorType.DOT11_BSSID.name(),
                "test",
                null,
                null,
                null,
                0,
                interval,
                5,
                "{}",
                false,
                status.name(),
                statusChangedAt,
                null,
                null,
                DateTime.now(),
                DateTime.now()
        );
    }

    @Test
    public void testIdleIsNeverStuck() {
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.IDLE, null, 5), MonitorStatus.IDLE));
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.IDLE, DateTime.now().minusDays(10), 5), MonitorStatus.IDLE));
    }

    @Test
    public void testUnknownStatusChangeTimeIsStuck() {
        assertTrue(MonitorsThread.isStuck(
                monitor(MonitorStatus.PENDING, null, 5), MonitorStatus.PENDING));
        assertTrue(MonitorsThread.isStuck(
                monitor(MonitorStatus.EXECUTING, null, 5), MonitorStatus.EXECUTING));
    }

    @Test
    public void testRecentStatusChangeIsNotStuck() {
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.PENDING, DateTime.now().minusMinutes(1), 5), MonitorStatus.PENDING));
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.EXECUTING, DateTime.now().minusMinutes(14), 1), MonitorStatus.EXECUTING));
    }

    @Test
    public void testMinimumTimeoutAppliesToShortIntervals() {
        // Interval of 1 minute: three intervals would be 3 minutes, but the minimum timeout is 15 minutes.
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.PENDING, DateTime.now().minusMinutes(10), 1), MonitorStatus.PENDING));
        assertTrue(MonitorsThread.isStuck(
                monitor(MonitorStatus.PENDING, DateTime.now().minusMinutes(16), 1), MonitorStatus.PENDING));
    }

    @Test
    public void testLongIntervalsGetThreeIntervals() {
        // Interval of 60 minutes: timeout is 180 minutes.
        assertFalse(MonitorsThread.isStuck(
                monitor(MonitorStatus.EXECUTING, DateTime.now().minusMinutes(170), 60), MonitorStatus.EXECUTING));
        assertTrue(MonitorsThread.isStuck(
                monitor(MonitorStatus.EXECUTING, DateTime.now().minusMinutes(181), 60), MonitorStatus.EXECUTING));
    }

}
