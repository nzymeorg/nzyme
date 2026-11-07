package app.nzyme.core.ethernet.l4.tcp;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class TcpSignatureTest {

    @Test
    public void testGuessInitialTtl() {
        assertEquals(TcpSignature.guessInitialTtl(64), 64);
        assertEquals(TcpSignature.guessInitialTtl(63), 64);
        assertEquals(TcpSignature.guessInitialTtl(128), 128);
        assertEquals(TcpSignature.guessInitialTtl(120), 128);
        assertEquals(TcpSignature.guessInitialTtl(30), 32);
        assertEquals(TcpSignature.guessInitialTtl(200), 255);
        assertEquals(TcpSignature.guessInitialTtl(255), 255);
    }

    @Test
    public void testFingerprintAndSignature() {
        TcpSignature s = new TcpSignature(64, 0, true, 12345, false, false, 64240, 1460, 7,
                List.of(2, 4, 8, 1, 3), 100L, 0L);

        assertEquals(s.stack(), "64:1:0:mss,sok,ts,nop,ws:-");
        assertEquals(s.fingerprint(), "64:1:0:mss,sok,ts,nop,ws:-:1460:7");
        assertEquals(s.signature(), "64:1:0:mss,sok,ts,nop,ws:-:1460:7:mss*44:0");
        assertEquals(s.hopDistance(), 0);
        assertTrue(s.isNative());
    }

    @Test
    public void testForwardedStackAndQuirks() {
        TcpSignature s = new TcpSignature(63, 0, true, 0, false, false, 65535, 1460, 7,
                List.of(2, 4, 8, 1, 3), 0L, 5L);

        assertEquals(s.stack(), "64:1:0:mss,sok,ts,nop,ws:ts0,ecr,id0");
        assertEquals(s.fingerprint(), "64:1:0:mss,sok,ts,nop,ws:ts0,ecr,id0:1460:7");
        assertEquals(s.signature(), "64:1:0:mss,sok,ts,nop,ws:ts0,ecr,id0:1460:7:max:0");
        assertEquals(s.hopDistance(), 1);
        assertFalse(s.isNative());
    }

    @Test
    public void testAbsentOptionsAndRawWindow() {
        TcpSignature s = new TcpSignature(128, 16, false, 7, true, true, 8192, null, null, List.of(), null, null);

        assertEquals(s.stack(), "128:0:1::-");
        assertEquals(s.fingerprint(), "128:0:1::-:0:-");
        assertEquals(s.signature(), "128:0:1::-:0:-:8192:16");
    }

    @Test
    public void testSourcePortRange() {
        assertEquals(TcpSignature.sourcePortRange(80), "0-1023");
        assertEquals(TcpSignature.sourcePortRange(1024), "1024-32767");
        assertEquals(TcpSignature.sourcePortRange(32768), "32768-49151");
        assertEquals(TcpSignature.sourcePortRange(49152), "49152-60999");
        assertEquals(TcpSignature.sourcePortRange(60999), "49152-60999");
        assertEquals(TcpSignature.sourcePortRange(61000), "61000-65535");
    }

    @Test
    public void testTimestampRate() {
        DateTime start = DateTime.now();

        assertNull(TcpSignature.timestampRate(null, start, 10L, start.plusSeconds(10)));
        assertNull(TcpSignature.timestampRate(0L, start, 1000L, start.plusSeconds(2)));
        assertEquals(TcpSignature.timestampRate(0L, start, 10000L, start.plusSeconds(10)), 1000);
        assertEquals(TcpSignature.timestampRate(5000L, start, 6000L, start.plusSeconds(10)), 100);
        // Wraparound.
        assertEquals(TcpSignature.timestampRate(0xFFFFFF00L, start, 0x00002000L, start.plusSeconds(10)), 845);

        assertEquals(TcpSignature.timestampRateBucket(1004), "1000");
        assertEquals(TcpSignature.timestampRateBucket(97), "100");
        assertEquals(TcpSignature.timestampRateBucket(250), "250");
        assertEquals(TcpSignature.timestampRateBucket(600), "other");
        assertNull(TcpSignature.timestampRateBucket(null));
    }

    @Test
    public void testIpIdBehavior() {
        assertNull(TcpSignature.ipIdBehavior(1, 5, 2));
        assertNull(TcpSignature.ipIdBehavior(1, null, 20));
        assertEquals(TcpSignature.ipIdBehavior(0, 0, 10), "ZERO");
        assertEquals(TcpSignature.ipIdBehavior(100, 109, 10), "SEQUENTIAL");
        assertEquals(TcpSignature.ipIdBehavior(65530, 3, 10), "SEQUENTIAL");
        assertEquals(TcpSignature.ipIdBehavior(100, 5000, 10), "OTHER");
    }

}
