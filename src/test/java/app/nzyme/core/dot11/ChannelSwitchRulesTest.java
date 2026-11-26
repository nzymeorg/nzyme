package app.nzyme.core.dot11;

import app.nzyme.core.dot11.channelswitches.ChannelSwitchAnomaly;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchMonitorRule;
import app.nzyme.core.dot11.channelswitches.ChannelSwitchRules;
import app.nzyme.core.rest.resources.taps.reports.tables.dot11.Dot11ChannelSwitchReport;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.*;

public class ChannelSwitchRulesTest {

    private static Dot11ChannelSwitchReport report(String frameType,
                                                   Integer frequency,
                                                   Integer operatingClass,
                                                   int channel,
                                                   int countMin,
                                                   int countMax,
                                                   long frames,
                                                   long timestampRepetitions,
                                                   long timestampMismatchFrames,
                                                   long interleavedBeacons,
                                                   long trailingElementFrames) {
        return Dot11ChannelSwitchReport.create("02:11:22:33:44:55", frameType, "CSA", 1, operatingClass, channel,
                frequency, countMin, countMax, frames, frames, timestampRepetitions, timestampMismatchFrames,
                interleavedBeacons, trailingElementFrames, "ACTION".equals(frameType) ? null : false, null, null, null);
    }

    private static Dot11ChannelSwitchReport toChannel14(String frameType, Boolean dsssOnly, String countryCode) {
        return Dot11ChannelSwitchReport.create("02:11:22:33:44:55", frameType, "CSA", 1, null, 14, 2437, 1, 3, 3, 3,
                1, 0, 0, 0, dsssOnly, countryCode, null, null);
    }

    private static Integer newFrequency(Integer frequency, Integer operatingClass, int channel) {
        return ChannelSwitchRules.newFrequency(frequency, operatingClass, channel);
    }

    private static boolean isInvalidChannel(Integer frequency, Integer operatingClass, int channel) {
        return ChannelSwitchRules.isInvalidChannel(frequency, operatingClass, channel, false);
    }

    @Test
    public void testNewFrequencyOfEachBand() {
        assertEquals(newFrequency(2437, null, 1), Integer.valueOf(2412));
        assertEquals(newFrequency(2437, null, 13), Integer.valueOf(2472));
        assertEquals(newFrequency(5180, null, 36), Integer.valueOf(5180));
        assertEquals(newFrequency(5180, null, 144), Integer.valueOf(5720));
        assertEquals(newFrequency(5180, null, 165), Integer.valueOf(5825));
        assertEquals(newFrequency(5180, null, 177), Integer.valueOf(5885));
        assertEquals(newFrequency(5955, null, 1), Integer.valueOf(5955));
        assertEquals(newFrequency(5955, null, 2), Integer.valueOf(5935));
        assertEquals(newFrequency(5955, null, 233), Integer.valueOf(7115));
    }

    @Test
    public void testChannelsThatDoNotExistInTheBand() {
        for (int channel : new int[]{0, 15, 36, 255}) {
            assertNull(newFrequency(2437, null, channel), "2.4 GHz channel " + channel);
            assertTrue(isInvalidChannel(2437, null, channel), "2.4 GHz channel " + channel);
        }

        assertEquals(newFrequency(2437, null, 14), Integer.valueOf(2484));
        assertTrue(isInvalidChannel(2437, null, 14));

        for (int channel : new int[]{0, 1, 14, 34, 38, 68, 96, 148, 150, 166, 181}) {
            assertNull(newFrequency(5180, null, channel), "5 GHz channel " + channel);
            assertTrue(isInvalidChannel(5180, null, channel), "5 GHz channel " + channel);
        }

        for (int channel : new int[]{0, 3, 4, 14, 235, 237}) {
            assertNull(newFrequency(5955, null, channel), "6 GHz channel " + channel);
            assertTrue(isInvalidChannel(5955, null, channel), "6 GHz channel " + channel);
        }
    }

    @Test
    public void testGlobalOperatingClassNamesTheBand() {
        assertEquals(newFrequency(2437, 128, 100), Integer.valueOf(5500));
        assertEquals(newFrequency(5180, 81, 6), Integer.valueOf(2437));
        assertEquals(newFrequency(5180, 131, 5), Integer.valueOf(5975));
        assertTrue(isInvalidChannel(5180, 81, 36));
    }

    @Test
    public void testCountrySpecificOperatingClassStaysInTheBand() {
        assertEquals(newFrequency(5180, 1, 44), Integer.valueOf(5220));
        assertEquals(newFrequency(2437, 12, 11), Integer.valueOf(2462));
    }

    @Test
    public void testUnknownBandIsNotInvalid() {
        assertNull(newFrequency(null, null, 14));
        assertFalse(isInvalidChannel(null, null, 14));
        assertFalse(isInvalidChannel(null, 1, 200));
        assertTrue(isInvalidChannel(null, 81, 14));
        assertFalse(ChannelSwitchRules.isInvalidChannel(null, 81, 14, true));
    }

    @Test
    public void testChannel14IsOpenTo80211bInJapanOnly() {
        assertEquals(ChannelSwitchRules.newFrequency(toChannel14("BEACON", true, "JP")), Integer.valueOf(2484));
        assertEquals(ChannelSwitchRules.anomalies(toChannel14("BEACON", true, "JP")), List.of());
        assertEquals(ChannelSwitchRules.anomalies(toChannel14("BEACON", true, null)), List.of());

        for (Dot11ChannelSwitchReport report : List.of(
                toChannel14("BEACON", false, "JP"),
                toChannel14("BEACON", false, null),
                toChannel14("BEACON", true, "US"),
                toChannel14("PROBE_RESPONSE", false, "JP"),
                toChannel14("BEACON", null, "DE"))) {
            assertEquals(ChannelSwitchRules.newFrequency(report), Integer.valueOf(2484));
            assertEquals(ChannelSwitchRules.anomalies(report), List.of(ChannelSwitchAnomaly.INVALID_CHANNEL));
        }
    }

    @Test
    public void testChannel14IsNotJudgedWithoutKnowingTheAccessPoint() {
        assertEquals(ChannelSwitchRules.newFrequency(toChannel14("ACTION", null, null)), Integer.valueOf(2484));
        assertEquals(ChannelSwitchRules.anomalies(toChannel14("ACTION", null, null)), List.of());
    }

    @Test
    public void testGenuineCountdownHasNoAnomalies() {
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 5, 1, 0, 0, 0)), List.of());
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 5180, 128, 100, 0, 50, 50, 2, 2, 2, 0)), List.of());
    }

    @Test
    public void testReplayedBurstHasEveryAnomaly() {
        assertEquals(
                ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 14, 0, 0, 1856, 1856, 832, 85, 1856)),
                List.of(ChannelSwitchAnomaly.values())
        );
    }

    @Test
    public void testThresholds() {
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 8, 7, 0, 0, 0)), List.of());
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 8, 8, 0, 0, 0)),
                List.of(ChannelSwitchAnomaly.REPEATED_TIMESTAMP));
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 8, 1, 3, 0, 0)),
                List.of(ChannelSwitchAnomaly.TIMESTAMP_MISMATCH));
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 8, 1, 2, 3, 0)),
                List.of(ChannelSwitchAnomaly.INTERLEAVED_BEACONS));
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 0, 0, 7, 1, 0, 0, 0)), List.of());
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 0, 0, 8, 1, 0, 0, 0)),
                List.of(ChannelSwitchAnomaly.STATIC_COUNT));
        assertEquals(ChannelSwitchRules.anomalies(report("BEACON", 2437, null, 11, 1, 5, 5, 1, 0, 0, 1)),
                List.of(ChannelSwitchAnomaly.TRAILING_ELEMENT));
    }

    @Test
    public void testProbeResponsesAndActionFramesAreNotJudgedByTimestampsOrCount() {
        for (String frameType : new String[]{"PROBE_RESPONSE", "ACTION"}) {
            assertEquals(ChannelSwitchRules.anomalies(report(frameType, 2437, null, 11, 0, 0, 100, 100, 100, 0, 100)),
                    List.of());
            assertEquals(ChannelSwitchRules.anomalies(report(frameType, 2437, null, 15, 0, 0, 100, 100, 100, 0, 100)),
                    List.of(ChannelSwitchAnomaly.INVALID_CHANNEL));
        }
    }

    @Test
    public void testBeaconsWithoutTheAnnouncementBetweenAnyFramesWithIt() {
        for (String frameType : new String[]{"BEACON", "PROBE_RESPONSE", "ACTION"}) {
            assertEquals(ChannelSwitchRules.anomalies(report(frameType, 2437, null, 11, 1, 5, 5, 1, 0, 2, 0)), List.of());
            assertEquals(ChannelSwitchRules.anomalies(report(frameType, 2437, null, 11, 1, 5, 5, 1, 0, 3, 0)),
                    List.of(ChannelSwitchAnomaly.INTERLEAVED_BEACONS));
        }
    }

    @Test
    public void testEveryAlertingAnomalyCanBeDisabled() {
        assertEquals(ChannelSwitchMonitorRule.enabledAnomalies(Set.of()), ChannelSwitchAnomaly.alertingNames());
        assertEquals(ChannelSwitchMonitorRule.enabledAnomalies(Set.of(ChannelSwitchMonitorRule.values())), List.of());
    }

    @Test
    public void testUnknownDisabledMethodsAreSkipped() {
        assertEquals(ChannelSwitchMonitorRule.parse(List.of("BURST_RATE", "NO_SUCH_RULE", "burst_rate", "")),
                Set.of(ChannelSwitchMonitorRule.BURST_RATE));
    }

    @Test
    public void testAlertingAnomalies() {
        assertEquals(ChannelSwitchAnomaly.alertingNames(),
                List.of("INVALID_CHANNEL", "REPEATED_TIMESTAMP", "TIMESTAMP_MISMATCH", "INTERLEAVED_BEACONS"));
    }

}
