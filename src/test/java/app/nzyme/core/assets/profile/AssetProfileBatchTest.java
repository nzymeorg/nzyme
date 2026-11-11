package app.nzyme.core.assets.profile;

import com.google.common.net.InetAddresses;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AssetProfileBatchTest {

    @Test
    public void testIsBroadcast() {
        assertTrue(AssetProfileBatch.isBroadcast("FF:FF:FF:FF:FF:FF", InetAddresses.forString("255.255.255.255")));
        assertTrue(AssetProfileBatch.isBroadcast(null, InetAddresses.forString("255.255.255.255")));
        assertTrue(AssetProfileBatch.isBroadcast("FF:FF:FF:FF:FF:FF", InetAddresses.forString("192.168.0.255")));
        assertTrue(AssetProfileBatch.isBroadcast("ff:ff:ff:ff:ff:ff", InetAddresses.forString("10.0.0.127")));

        assertFalse(AssetProfileBatch.isBroadcast("D8:B3:70:5B:5C:6E", InetAddresses.forString("98.83.193.255")));
        assertFalse(AssetProfileBatch.isBroadcast("D8:B3:70:5B:5C:6E", InetAddresses.forString("10.0.0.255")));
        assertFalse(AssetProfileBatch.isBroadcast(null, InetAddresses.forString("192.168.0.255")));
        assertFalse(AssetProfileBatch.isBroadcast(null, InetAddresses.forString("fe80::1")));
    }

}
