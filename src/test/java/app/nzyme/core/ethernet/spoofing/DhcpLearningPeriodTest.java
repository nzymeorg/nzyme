package app.nzyme.core.ethernet.spoofing;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DhcpLearningPeriodTest {

    private static final DateTime FIRST = new DateTime(2026, 11, 1, 12, 0);

    @Test
    public void testStartsWithTheFirstServerOfTheProtocol() {
        Spoofing.DhcpLearningPeriod period = Spoofing.dhcpLearningPeriod(FIRST, null, null, 24);

        assertEquals(period.start(), FIRST);
        assertEquals(period.end(), FIRST.plusHours(24));
        assertFalse(period.endedByUser());
    }

    @Test
    public void testRestartOnlyCountsAfterTheFirstServer() {
        assertEquals(Spoofing.dhcpLearningPeriod(FIRST, FIRST.plusDays(3), null, 24).start(), FIRST.plusDays(3));

        assertEquals(Spoofing.dhcpLearningPeriod(FIRST, FIRST.minusDays(30), null, 24).start(), FIRST);
    }

    @Test
    public void testEndedByUserOnlyWithinThePeriod() {
        Spoofing.DhcpLearningPeriod ended = Spoofing.dhcpLearningPeriod(FIRST, null, FIRST.plusHours(2), 24);
        assertEquals(ended.end(), FIRST.plusHours(2));
        assertTrue(ended.endedByUser());

        Spoofing.DhcpLearningPeriod later = Spoofing.dhcpLearningPeriod(FIRST, null, FIRST.minusDays(30), 24);
        assertEquals(later.end(), FIRST.plusHours(24));
        assertFalse(later.endedByUser());

        assertFalse(Spoofing.dhcpLearningPeriod(FIRST, null, FIRST.plusDays(2), 24).endedByUser());
    }

    @Test
    public void testDhcpLearnsFromItsFirstServer() {
        assertEquals(Spoofing.dhcpLearningStart(DhcpSpoofingDetector.PROTOCOL_DHCP, FIRST, FIRST.minusDays(30)), FIRST);
        assertNull(Spoofing.dhcpLearningStart(DhcpSpoofingDetector.PROTOCOL_DHCP, null, FIRST.minusDays(30)));
    }

    @Test
    public void testIpv6ProtocolsLearnFromWhenDetectionStartedToLook() {
        DateTime started = FIRST.minusDays(30);
        for (String protocol : new String[]{DhcpSpoofingDetector.PROTOCOL_DHCP6, DhcpSpoofingDetector.PROTOCOL_ROUTER_ADVERTISEMENT}) {
            assertEquals(Spoofing.dhcpLearningStart(protocol, FIRST, started), started);
            assertEquals(Spoofing.dhcpLearningStart(protocol, null, started), started);
            assertTrue(Spoofing.dhcpLearningPeriod(Spoofing.dhcpLearningStart(protocol, FIRST, started), null, null, 24)
                    .end().isBefore(FIRST));
        }
    }

    @Test
    public void testIpv6ProtocolsKeepTheLearningOfServersKnownBefore() {
        DateTime started = FIRST.plusDays(30);
        assertEquals(Spoofing.dhcpLearningStart(DhcpSpoofingDetector.PROTOCOL_ROUTER_ADVERTISEMENT, FIRST, started), FIRST);
        assertEquals(Spoofing.dhcpLearningStart(DhcpSpoofingDetector.PROTOCOL_DHCP6, FIRST, null), FIRST);
    }

}
