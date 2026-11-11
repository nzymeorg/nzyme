package app.nzyme.core.assets.identity;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class DuplicateMacDetectorTest {

    @Test
    public void testStrongPartDropsWindowScaleMssAndEcn() {
        assertEquals(DuplicateMacDetector.strongPart("64:1:0:mss,sok,ts,nop,ws:-:1460:7"), "64:1:-:mss,sok,ts,nop,ws:-");
        assertEquals(DuplicateMacDetector.strongPart("64:1:0:mss,sok,ts,nop,ws:-:1400:9"), "64:1:-:mss,sok,ts,nop,ws:-");
        assertEquals(DuplicateMacDetector.strongPart("128:1:0:mss,nop,ws,nop,nop,sok:ts0:1460:8"),
                "128:1:-:mss,nop,ws,nop,nop,sok:ts0");
        assertEquals(DuplicateMacDetector.strongPart("128:0:1::-:0:-"), "128:0:-::-");
        assertEquals(DuplicateMacDetector.strongPart("garbage"), "garbage");

        String ecnOff = DuplicateMacDetector.strongPart("64:1:0:mss,nop,ws,nop,nop,ts,sok:id0:1460:6");
        assertEquals(DuplicateMacDetector.strongPart("64:1:1:mss,nop,ws,nop,nop,ts,sok:id0:1460:6"), ecnOff);
        assertEquals(DuplicateMacDetector.strongPart("64:1:1:mss,nop,ws,30,nop,nop,ts,sok:id0:1460:6"), ecnOff);

        assertNotEquals(DuplicateMacDetector.strongPart("128:1:0:mss,nop,ws,nop,nop,sok:-:1460:8"), ecnOff);
        assertNotEquals(DuplicateMacDetector.strongPart("64:1:0:mss,sok,ts,nop,ws:-:1460:7"), ecnOff);
    }

    @Test
    public void testTimestampVariantOfOneStackIsNotADuplicate() {
        String windows = "128:1:0:mss,nop,ws,nop,nop,sok:-:1460:8";
        String windowsTimestamps = "128:1:0:mss,nop,ws,sok,ts:-:1460:8";
        String linux = "64:1:0:mss,sok,ts,nop,ws:-:1460:7";

        assertFalse(DuplicateMacDetector.hasDifferentStacks(List.of(windows), true));
        assertFalse(DuplicateMacDetector.hasDifferentStacks(List.of(windows, windowsTimestamps), true));
        assertFalse(DuplicateMacDetector.hasDifferentStacks(
                List.of(windows, windowsTimestamps, "128:1:0:mss,nop,ws,nop,nop,sok:-:1380:2"), true));
        assertTrue(DuplicateMacDetector.hasDifferentStacks(List.of(windows, windowsTimestamps, linux), true));

        assertTrue(DuplicateMacDetector.hasDifferentStacks(List.of("64:1:0:mss,nop,ws,nop,nop,sok:id0:1460:6",
                "64:1:0:mss,nop,ws,nop,nop,ts,sok:id0:1460:6", "64:1:0:mss,nop,ws,sok,ts:id0:1460:6"), true));

        assertTrue(DuplicateMacDetector.hasDifferentStacks(List.of(windows, windowsTimestamps), false));
    }

    @Test
    public void testExclusionMatcher() {
        AssetIdentityExclusionMatcher full = AssetIdentityExclusionMatcher.create("aa:bb:cc:dd:ee:ff", null);
        assertEquals(full.matches("AA:BB:CC:DD:EE:FF", AssetIdentityType.DUPLICATE_MAC), true);
        assertEquals(full.matches("AA:BB:CC:DD:EE:00", AssetIdentityType.DUPLICATE_MAC), false);

        AssetIdentityExclusionMatcher prefix = AssetIdentityExclusionMatcher.create("aa-bb-cc", java.util.List.of("mac_change"));
        assertEquals(prefix.isPrefix(), true);
        assertEquals(prefix.matches("AA:BB:CC:00:11:22", AssetIdentityType.MAC_CHANGE), true);
        assertEquals(prefix.matches("AA:BB:CC:00:11:22", AssetIdentityType.DUPLICATE_MAC), false);
    }

}
