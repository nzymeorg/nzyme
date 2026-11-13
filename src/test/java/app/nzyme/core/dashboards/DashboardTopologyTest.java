package app.nzyme.core.dashboards;

import app.nzyme.core.rest.responses.ethernet.topology.TopologyLiveResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyLiveSessionResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyNodeAssetResponse;
import app.nzyme.core.rest.responses.ethernet.topology.TopologyNodeResponse;
import app.nzyme.core.util.filters.FilterParser;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

public class DashboardTopologyTest {

    private static final byte[] KEY = new byte[32];
    private static final UUID TAP = UUID.fromString("7a6f1f0c-5c0f-4d0e-9d55-0c4c9d6f0a11");

    private static TopologyLiveResponse live() {
        DateTime now = DateTime.parse("2026-11-12T12:00:00Z");

        return TopologyLiveResponse.create(now, 60, List.of(
                TopologyNodeResponse.create("host:10.1.2.3", "INTERNAL", "HOST", "10.1.2.3", null, null, null,
                        TopologyNodeAssetResponse.create(UUID.randomUUID(), "AA:BB:CC:00:11:22", "Payroll server"),
                        "payroll.corp.example", null, null, null),
                TopologyNodeResponse.create("ext:93.184.216.34", "EXTERNAL", "HOST", "93.184.216.34", null, null,
                        null, null, null, 15133, "EDGECAST", "US"),
                TopologyNodeResponse.create("cc:DE", "EXTERNAL", "COUNTRY", null, null, null, null, null, null,
                        null, null, "DE"),
                TopologyNodeResponse.create("tap:" + TAP, "INTERNAL", "TAP", null, null, TAP, "Datacenter", null,
                        null, null, null, null)
        ), List.of(), List.of(), List.of(
                TopologyLiveSessionResponse.create("3f2a9c", TAP, "TCP", "host:10.1.2.3", "ext:93.184.216.34",
                        "10.1.2.3", "93.184.216.34", 443, now.minusSeconds(20), now.minusSeconds(2), 1200, 88000,
                        38.9, -77.0, "Ashburn", "US")
        ), 2000, false, true);
    }

    @Test
    public void testRedactedHidesAddressesAndNames() {
        TopologyLiveResponse redacted = DashboardTopology.redacted(live(), new DashboardPseudonyms.Scope(KEY, "a"));
        String json = FilterParser.OM.writeValueAsString(redacted);

        for (String secret : List.of("10.1.2.3", "93.184.216.34", "payroll", "Payroll", "AA:BB:CC", "EDGECAST",
                "15133", "Ashburn", "3f2a9c", TAP.toString(), "38.9", "\"US\"")) {
            assertFalse(json.contains(secret), secret + " in " + json);
        }

        assertEquals(redacted.nodes().size(), 4);
        assertEquals(redacted.nodes().get(0).type(), "INTERNAL");
        assertEquals(redacted.nodes().get(0).level(), "HOST");
        assertEquals(redacted.nodes().get(2).countryCode(), "DE");
        assertEquals(redacted.nodes().get(3).tapName(), "Datacenter");

        TopologyLiveSessionResponse session = redacted.sessions().get(0);
        assertEquals(session.source(), redacted.nodes().get(0).id());
        assertEquals(session.target(), redacted.nodes().get(1).id());
        assertEquals(session.bytesTx(), 1200);
        assertEquals(session.bytesRx(), 88000);
        assertEquals(session.destinationPort(), 443);
        assertTrue(redacted.mergeTaps());
    }

    @Test
    public void testPseudonymsAreStableAndScoped() {
        TopologyLiveResponse first = DashboardTopology.redacted(live(), new DashboardPseudonyms.Scope(KEY, "a"));
        TopologyLiveResponse again = DashboardTopology.redacted(live(), new DashboardPseudonyms.Scope(KEY, "a"));
        TopologyLiveResponse other = DashboardTopology.redacted(live(), new DashboardPseudonyms.Scope(KEY, "b"));

        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        TopologyLiveResponse rekeyed = DashboardTopology.redacted(live(), new DashboardPseudonyms.Scope(otherKey, "a"));

        assertEquals(again.nodes().get(0).id(), first.nodes().get(0).id());
        assertEquals(again.sessions().get(0).sessionKey(), first.sessions().get(0).sessionKey());
        assertEquals(again.sessions().get(0).tapUuid(), first.sessions().get(0).tapUuid());
        assertNotEquals(other.nodes().get(0).id(), first.nodes().get(0).id());
        assertNotEquals(rekeyed.nodes().get(0).id(), first.nodes().get(0).id());
        assertNotEquals(first.nodes().get(0).id(), first.nodes().get(1).id());
    }

}
