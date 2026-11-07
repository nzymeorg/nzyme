package app.nzyme.core.assets.identity;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DuplicateMacDetectorTest {

    @Test
    public void testStrongPartDropsWindowScaleAndMss() {
        assertEquals(DuplicateMacDetector.strongPart("64:1:0:mss,sok,ts,nop,ws:-:1460:7"), "64:1:0:mss,sok,ts,nop,ws:-");
        assertEquals(DuplicateMacDetector.strongPart("64:1:0:mss,sok,ts,nop,ws:-:1400:9"), "64:1:0:mss,sok,ts,nop,ws:-");
        assertEquals(DuplicateMacDetector.strongPart("128:1:0:mss,nop,ws,nop,nop,sok:ts0:1460:8"),
                "128:1:0:mss,nop,ws,nop,nop,sok:ts0");
        assertEquals(DuplicateMacDetector.strongPart("128:0:1::-:0:-"), "128:0:1::-");
        assertEquals(DuplicateMacDetector.strongPart("garbage"), "garbage");
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
