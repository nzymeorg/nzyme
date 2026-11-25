package app.nzyme.core.ethernet;

import org.testng.annotations.Test;

import java.net.InetAddress;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class Nat64Test {

    private static InetAddress address(String address) throws Exception {
        return InetAddress.getByName(address);
    }

    @Test
    public void testEmbeddedAddressOfTheWellKnownPrefix() throws Exception {
        assertEquals(Nat64.embeddedAddress(address("64:ff9b::c000:221")).orElseThrow(), address("192.0.2.33"));
        assertEquals(Nat64.embeddedAddress(address("64:ff9b::808:808")).orElseThrow(), address("8.8.8.8"));
        assertEquals(Nat64.embeddedAddress(address("64:ff9b::ffff:ffff")).orElseThrow(), address("255.255.255.255"));
    }

    @Test
    public void testOtherAddressesCarryNothing() throws Exception {
        assertTrue(Nat64.embeddedAddress(address("2001:db8::c000:221")).isEmpty());
        assertTrue(Nat64.embeddedAddress(address("64:ff9b:1::c000:221")).isEmpty());
        assertTrue(Nat64.embeddedAddress(address("64:ff9b:0:0:1::c000:221")).isEmpty());
        assertTrue(Nat64.embeddedAddress(address("192.0.2.33")).isEmpty());
        assertTrue(Nat64.embeddedAddress(address("::ffff:192.0.2.33")).isEmpty());
        assertTrue(Nat64.embeddedAddress(address("fe80::1")).isEmpty());
    }

    @Test
    public void testDestination() throws Exception {
        assertEquals(Nat64.destination(address("64:ff9b::c000:221")), address("192.0.2.33"));
        assertEquals(Nat64.destination(address("2001:db8::1")), address("2001:db8::1"));
        assertEquals(Nat64.destination(address("10.0.0.1")), address("10.0.0.1"));
    }

}
