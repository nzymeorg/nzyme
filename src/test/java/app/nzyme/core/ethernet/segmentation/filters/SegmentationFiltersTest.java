package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

public class SegmentationFiltersTest {

    private static final String MEMBERS = "SELECT 1 FROM segmentation_segment_members AS m WHERE m.segment_id = s.id AND ";
    private static final String TARGETS = "SELECT 1 FROM jsonb_array_elements(f.targets) AS elem WHERE ";

    @Test
    public void testAnAddressFindsTheSegmentsThatContainIt() {
        SegmentFilters filters = new SegmentFilters();

        assertEquals(filters.buildSql("b", "network", FilterOperator.EQUALS).where(),
                "EXISTS (" + MEMBERS + "m.network >>= :b::inet)");
        assertEquals(filters.buildSql("b", "network", FilterOperator.NOT_EQUALS).where(),
                "NOT EXISTS (" + MEMBERS + "m.network >>= :b::inet)");

        assertEquals(filters.buildSql("b", "network", FilterOperator.IN_CIDR).where(),
                "EXISTS (" + MEMBERS + "m.network <<= :b::cidr)");
        assertEquals(filters.buildSql("b", "network", FilterOperator.NOT_IN_CIDR).where(),
                "NOT EXISTS (" + MEMBERS + "m.network <<= :b::cidr)");
    }

    @Test
    public void testANegationOnTheMembersOfASegmentHoldsIfNoMemberMatches() {
        SegmentFilters filters = new SegmentFilters();

        assertEquals(filters.buildSql("b", "asn", FilterOperator.NOT_EQUALS_NUMERIC).where(),
                "NOT EXISTS (" + MEMBERS + "m.asn = :b)");
        assertEquals(filters.buildSql("b", "asn", FilterOperator.EQUALS_NUMERIC).where(),
                "EXISTS (" + MEMBERS + "m.asn = :b)");
    }

    @Test
    public void testAFindingMatchesIfOneOfItsTargetsDoes() {
        FindingFilters filters = new FindingFilters();

        assertEquals(filters.buildSql("b", "target_address", FilterOperator.EQUALS).where(),
                "EXISTS (" + TARGETS + "host((elem ->> 'address')::inet) = host(:b::inet))");
        assertEquals(filters.buildSql("b", "target_address", FilterOperator.IN_CIDR).where(),
                "EXISTS (" + TARGETS + "(elem ->> 'address')::inet <<= :b::cidr)");
        assertEquals(filters.buildSql("b", "target_port", FilterOperator.GREATER_THAN).where(),
                "EXISTS (" + TARGETS + "(elem ->> 'port')::int > :b)");
    }

    @Test
    public void testANegationOnTheTargetsOfAFindingHoldsIfNoTargetMatches() {
        FindingFilters filters = new FindingFilters();

        assertEquals(filters.buildSql("b", "target_address", FilterOperator.NOT_EQUALS).where(),
                "NOT EXISTS (" + TARGETS + "host((elem ->> 'address')::inet) = host(:b::inet))");
        assertEquals(filters.buildSql("b", "target_address", FilterOperator.NOT_IN_CIDR).where(),
                "NOT EXISTS (" + TARGETS + "(elem ->> 'address')::inet <<= :b::cidr)");
        assertEquals(filters.buildSql("b", "target_port", FilterOperator.NOT_EQUALS_NUMERIC).where(),
                "NOT EXISTS (" + TARGETS + "(elem ->> 'port')::int = :b)");
    }

    @Test
    public void testOnlyTheSessionsOfAFlowAreFilteredAfterAddingUp() {
        FlowFilters filters = new FlowFilters();

        GeneratedSql sessions = filters.buildSql("b", "sessions", FilterOperator.GREATER_THAN);
        assertTrue(sessions.where().isEmpty());
        assertEquals(sessions.having(), "(sessions + continued) > :b");

        for (Map.Entry<String, FilterOperator> field : Map.of(
                "source_address", FilterOperator.EQUALS,
                "destination_address", FilterOperator.IN_CIDR,
                "source_asn", FilterOperator.EQUALS_NUMERIC,
                "destination_asn", FilterOperator.EQUALS_NUMERIC,
                "source_country_code", FilterOperator.EQUALS,
                "destination_country_code", FilterOperator.EQUALS,
                "l4_type", FilterOperator.EQUALS,
                "port", FilterOperator.EQUALS_NUMERIC,
                "protocol", FilterOperator.CONTAINS,
                "outcome", FilterOperator.NOT_EQUALS).entrySet()) {
            GeneratedSql sql = filters.buildSql("b", field.getKey(), field.getValue());
            assertFalse(sql.where().isEmpty(), field.getKey());
            assertTrue(sql.having().isEmpty(), field.getKey());
        }

        assertEquals(filters.buildSql("b", "port", FilterOperator.EQUALS_NUMERIC).where(), "destination_port = :b");
    }

    @Test
    public void testPoliciesWithoutFindingsCountAsZero() {
        PolicyFilters filters = new PolicyFilters();

        assertEquals(filters.buildSql("b", "crossed_findings", FilterOperator.EQUALS_NUMERIC).where(),
                "COALESCE(f.crossed_findings, 0) = :b");
        assertEquals(filters.buildSql("b", "attempted_findings", FilterOperator.GREATER_THAN).where(),
                "COALESCE(f.attempted_findings, 0) > :b");
    }

    @Test
    public void testEveryFieldOfTheListsIsKnown() {
        fields(new SegmentFilters(), Map.of("name", FilterOperator.EQUALS, "description", FilterOperator.CONTAINS,
                "kind", FilterOperator.EQUALS, "network", FilterOperator.EQUALS, "asn", FilterOperator.EQUALS_NUMERIC));
        fields(new PolicyFilters(), Map.of("name", FilterOperator.EQUALS, "description", FilterOperator.CONTAINS,
                "status", FilterOperator.NOT_EQUALS, "alert_on_connected", FilterOperator.BOOLEAN,
                "alert_on_attempted", FilterOperator.BOOLEAN, "crossed_findings", FilterOperator.GREATER_THAN,
                "attempted_findings", FilterOperator.SMALLER_THAN));
        fields(new FindingFilters(), Map.of("policy_name", FilterOperator.EQUALS, "finding_class", FilterOperator.EQUALS,
                "source_address", FilterOperator.IN_CIDR, "source_asn", FilterOperator.EQUALS_NUMERIC,
                "source_country_code", FilterOperator.EQUALS, "target_address", FilterOperator.EQUALS,
                "target_port", FilterOperator.EQUALS_NUMERIC, "audit", FilterOperator.BOOLEAN,
                "session_count", FilterOperator.GREATER_THAN, "target_count", FilterOperator.SMALLER_THAN));
        fields(new FindingTargetFilters(), Map.of("address", FilterOperator.EQUALS, "l4_type", FilterOperator.EQUALS,
                "port", FilterOperator.EQUALS_NUMERIC, "protocol", FilterOperator.EQUALS,
                "outcome", FilterOperator.NOT_EQUALS, "sessions", FilterOperator.GREATER_THAN));
        fields(new GroupAddressFilters(), Map.of("address", FilterOperator.IN_CIDR,
                "sessions_from", FilterOperator.GREATER_THAN, "sessions_to", FilterOperator.EQUALS_NUMERIC,
                "answered", FilterOperator.SMALLER_THAN));
    }

    @Test
    public void testUnknownFieldsAndOperatorsAreRejected() {
        for (SqlFilterProvider filters : new SqlFilterProvider[]{new SegmentFilters(), new PolicyFilters(),
                new FindingFilters(), new FindingTargetFilters(), new FlowFilters(), new GroupAddressFilters()}) {
            assertThrows(RuntimeException.class, () -> filters.buildSql("b", "no_such_field", FilterOperator.EQUALS));
        }

        assertThrows(RuntimeException.class, () -> new SegmentFilters().buildSql("b", "network", FilterOperator.CONTAINS));
        assertThrows(RuntimeException.class, () -> new FindingFilters().buildSql("b", "target_port", FilterOperator.REGEX_MATCH));
        assertThrows(RuntimeException.class, () -> new FlowFilters().buildSql("b", "sessions", FilterOperator.EQUALS));
        assertThrows(RuntimeException.class, () -> new GroupAddressFilters().buildSql("b", "answered", FilterOperator.EQUALS));
    }

    private static void fields(SqlFilterProvider filters, Map<String, FilterOperator> fields) {
        for (Map.Entry<String, FilterOperator> field : fields.entrySet()) {
            GeneratedSql sql = filters.buildSql("b", field.getKey(), field.getValue());
            assertFalse(sql.where().isEmpty(), field.getKey());
            assertTrue(sql.having().isEmpty(), field.getKey());
        }
    }

}
