package app.nzyme.core.ethernet.beacons;

import app.nzyme.core.ethernet.InternalAddresses;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class BeaconDetectorTest {

    @Test
    public void testIsValidAddress() {
        assertTrue(BeaconDetector.isValidAddress("10.0.0.1"));
        assertTrue(BeaconDetector.isValidAddress("2001:db8::1"));
        assertFalse(BeaconDetector.isValidAddress("example.com"));
        assertFalse(BeaconDetector.isValidAddress("localhost"));
        assertFalse(BeaconDetector.isValidAddress(null));
        assertFalse(BeaconDetector.isValidAddress(""));
    }

    @Test
    public void testIsSiteLocal() {
        assertTrue(BeaconDetector.isSiteLocal("10.0.0.1", InternalAddresses.Rules.DEFAULT));
        assertTrue(BeaconDetector.isSiteLocal("192.168.1.1", InternalAddresses.Rules.DEFAULT));
        assertTrue(BeaconDetector.isSiteLocal("172.16.5.5", InternalAddresses.Rules.DEFAULT));
        assertTrue(BeaconDetector.isSiteLocal("127.0.0.1", InternalAddresses.Rules.DEFAULT));
        assertTrue(BeaconDetector.isSiteLocal("169.254.1.1", InternalAddresses.Rules.DEFAULT));
        assertFalse(BeaconDetector.isSiteLocal("8.8.8.8", InternalAddresses.Rules.DEFAULT));
        assertFalse(BeaconDetector.isSiteLocal("2606:4700::1111", InternalAddresses.Rules.DEFAULT));
        assertFalse(BeaconDetector.isSiteLocal("example.com", InternalAddresses.Rules.DEFAULT));
    }

    @Test
    public void testIsReverseLookupName() {
        assertTrue(BeaconDetector.isReverseLookupName("1.0.0.10.in-addr.arpa"));
        assertTrue(BeaconDetector.isReverseLookupName("b.a.9.8.7.6.5.0.4.0.0.0.3.0.0.0.2.0.0.0.1.0.0.0.0.0.0.0.1.2.3.4.ip6.arpa"));
        assertFalse(BeaconDetector.isReverseLookupName("example.com"));
    }

    @Test
    public void testIsLocalDomainName() {
        assertTrue(BeaconDetector.isLocalDomainName("printer"));
        assertTrue(BeaconDetector.isLocalDomainName("nas.local"));
        assertTrue(BeaconDetector.isLocalDomainName("dc01.corp"));
        assertTrue(BeaconDetector.isLocalDomainName("router.home.arpa"));
        assertFalse(BeaconDetector.isLocalDomainName("example.com"));
        assertFalse(BeaconDetector.isLocalDomainName("telemetry.example.org"));
    }

    @Test
    public void testIsMulticast() {
        assertTrue(BeaconDetector.isMulticast("224.0.0.251"));
        assertTrue(BeaconDetector.isMulticast("239.255.255.250"));
        assertTrue(BeaconDetector.isMulticast("ff02::fb"));
        assertFalse(BeaconDetector.isMulticast("95.216.195.133"));
        assertFalse(BeaconDetector.isMulticast("192.168.0.1"));
        assertFalse(BeaconDetector.isMulticast("example.com"));
        assertFalse(BeaconDetector.isMulticast(null));
    }

    @Test
    public void testIsMoreSpecificType() {
        assertTrue(BeaconDetector.isMoreSpecificType(BeaconType.HTTP, "TCP"));
        assertTrue(BeaconDetector.isMoreSpecificType(BeaconType.SSH, "TCP"));
        assertTrue(BeaconDetector.isMoreSpecificType(BeaconType.TLS, "TCP"));
        assertTrue(BeaconDetector.isMoreSpecificType(BeaconType.HTTP, "UDP"));
        assertFalse(BeaconDetector.isMoreSpecificType(BeaconType.TCP, "HTTP"));
        assertFalse(BeaconDetector.isMoreSpecificType(BeaconType.TCP, "TCP"));
        assertFalse(BeaconDetector.isMoreSpecificType(BeaconType.HTTP, "HTTP"));
        assertFalse(BeaconDetector.isMoreSpecificType(BeaconType.DNS, "TCP"));
        assertFalse(BeaconDetector.isMoreSpecificType(BeaconType.SOCKS, "TCP"));
    }

}
