package app.nzyme.core.ethernet.topology;

import app.nzyme.core.ethernet.l4.filters.L4Filters;
import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TopologyFiltersTest {

    @Test
    public void testDurationIsARowCondition() {
        GeneratedSql sql = new TopologyFilters().buildSql("b", "duration", FilterOperator.GREATER_THAN);

        // The session list filters durations after grouping by session. The topology has no such grouping.
        assertTrue(sql.having().isEmpty());
        assertEquals(sql.where(),
                "(EXTRACT(EPOCH FROM (most_recent_segment_time - start_time)) * 1000) > :b");
    }

    @Test
    public void testOtherFieldsAreTheOnesOfTheSessionList() {
        L4Filters l4 = new L4Filters();
        TopologyFilters topology = new TopologyFilters();

        for (String field : new String[]{"l4_type", "source_mac", "source_address", "source_port", "destination_mac",
                "destination_address", "destination_port", "bytes_rx_count", "bytes_tx_count", "segments_count",
                "state", "session_key", "source_address_geo_asn_number", "source_address_geo_city",
                "source_address_geo_country_code", "destination_address_geo_asn_number",
                "destination_address_geo_city", "destination_address_geo_country_code", "fingerprint", "address",
                "address_geo_asn_number", "address_geo_country_code", "address_geo_city"}) {
            assertEquals(topology.buildSql("b", field, FilterOperator.IS_NULL),
                    l4.buildSql("b", field, FilterOperator.IS_NULL), field);
        }

        assertEquals(topology.buildSql("b", "tags", FilterOperator.CONTAINS),
                l4.buildSql("b", "tags", FilterOperator.CONTAINS));
    }

    @Test
    public void testNoFilterNeedsAHavingClause() {
        // Every filter of the session list must work on rows, or the topology would silently ignore it.
        TopologyFilters topology = new TopologyFilters();
        for (String field : new String[]{"l4_type", "source_address", "destination_port", "state", "duration"}) {
            FilterOperator operator = field.equals("destination_port") || field.equals("duration")
                    ? FilterOperator.EQUALS_NUMERIC : FilterOperator.EQUALS;
            assertTrue(topology.buildSql("b", field, operator).having().isEmpty(), field);
        }
    }

    @Test
    public void testUnknownFieldIsRejected() {
        assertThrows(RuntimeException.class,
                () -> new TopologyFilters().buildSql("b", "no_such_field", FilterOperator.EQUALS));
    }

}
