package app.nzyme.core.ethernet;

import com.google.common.net.InetAddresses;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class InternalAddressesTest {

    private static final InternalAddresses.Rules CGNAT_IS_INTERNAL = new InternalAddresses.Rules(true);

    private static boolean siteLocal(InternalAddresses.Rules rules, String address) {
        return rules.isSiteLocal(InetAddresses.forString(address));
    }

    @Test
    public void testCgnatIsExternalByDefault() {
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "100.64.0.1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "100.100.100.100"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "10.0.0.1"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "172.16.5.5"));
        assertTrue(siteLocal(InternalAddresses.Rules.DEFAULT, "192.168.1.1"));
        assertFalse(siteLocal(InternalAddresses.Rules.DEFAULT, "8.8.8.8"));
    }

    @Test
    public void testCgnatIsInternalWhereConfigured() {
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.64.0.0"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.100.100.100"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "100.127.255.255"));
        assertTrue(siteLocal(CGNAT_IS_INTERNAL, "10.0.0.1"));
    }

    @Test
    public void testTapSettingIsReadFromTheReportedConfiguration() {
        assertTrue(InternalAddresses.tapSetting("{\"misc\":{\"cgnat_is_internal\":true}}"));
        assertFalse(InternalAddresses.tapSetting("{\"misc\":{\"cgnat_is_internal\":false}}"));
    }

    @Test
    public void testTapWithoutAReportedSettingHasTheDefault() {
        assertFalse(InternalAddresses.tapSetting(null));
        assertFalse(InternalAddresses.tapSetting(""));
        assertFalse(InternalAddresses.tapSetting("{}"));
        assertFalse(InternalAddresses.tapSetting("{\"misc\":{\"panic_log_file_path\":null}}"));
        assertFalse(InternalAddresses.tapSetting("{\"misc\":{\"cgnat_is_internal\":\"yes\"}}"));
        assertFalse(InternalAddresses.tapSetting("{\"misc\":[1,2]}"));
        assertFalse(InternalAddresses.tapSetting("not json"));
    }

    @Test
    public void testOnlyTheSharedAddressSpaceIsAdded() {
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "100.63.255.255"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "100.128.0.0"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "101.64.0.1"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "8.8.8.8"));
        assertFalse(siteLocal(CGNAT_IS_INTERNAL, "2606:4700:4700::1111"));
    }

}
