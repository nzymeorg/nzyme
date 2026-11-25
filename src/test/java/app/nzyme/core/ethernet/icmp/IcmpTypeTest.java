package app.nzyme.core.ethernet.icmp;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class IcmpTypeTest {

    @Test
    public void testTheSameNumberIsAnotherMessagePerVersion() {
        assertEquals(IcmpType.name(4, 3), "Destination Unreachable");
        assertEquals(IcmpType.name(6, 3), "Time Exceeded");
        assertEquals(IcmpType.name(4, 8), "Echo Request");
        assertEquals(IcmpType.name(6, 128), "Echo Request");
        assertEquals(IcmpType.name(6, 200), "Type 200");
    }

    @Test
    public void testErrorsPerVersion() {
        assertTrue(IcmpType.isError(4, 11));
        assertFalse(IcmpType.isError(6, 11));
        assertTrue(IcmpType.isError(6, 1));
        assertTrue(IcmpType.isError(6, 137));
        assertFalse(IcmpType.isError(6, 128));
        assertFalse(IcmpType.isError(4, 8));
    }

    @Test
    public void testTypeKeys() {
        assertEquals(IcmpSql.typeKey(4, 8), "8");
        assertEquals(IcmpSql.typeKey(6, 128), "6:128");
        assertEquals(IcmpSql.typeKey("p."),
                "(CASE WHEN p.ip_version = 6 THEN '6:' || p.icmp_type ELSE p.icmp_type::text END)");
    }

    @Test
    public void testConditionsNameTheVersion() {
        assertEquals(IcmpSql.unreachable(""), "((ip_version = 4 AND icmp_type = 3) OR (ip_version = 6 AND icmp_type = 1))");
        assertEquals(IcmpSql.portUnreachable("p."), "((p.ip_version = 4 AND p.icmp_type = 3 AND p.icmp_code = 3) OR "
                + "(p.ip_version = 6 AND p.icmp_type = 1 AND p.icmp_code = 4))");
    }

}
