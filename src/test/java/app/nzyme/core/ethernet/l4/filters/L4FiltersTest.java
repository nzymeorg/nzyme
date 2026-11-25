package app.nzyme.core.ethernet.l4.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L4FiltersTest {

    private static String where(String field, FilterOperator operator) {
        GeneratedSql sql = new L4Filters().buildSql("b", field, operator);
        assertTrue(sql.having().isEmpty());
        return sql.where();
    }

    @Test
    public void testEitherSideMatchesSourceOrDestination() {
        assertEquals(where("address_geo_asn_number", FilterOperator.EQUALS_NUMERIC),
                "((source_address_geo_asn_number = :b) OR (destination_address_geo_asn_number = :b))");
        assertEquals(where("address_geo_country_code", FilterOperator.EQUALS),
                "((source_address_geo_country_code = :b) OR (destination_address_geo_country_code = :b))");
        assertEquals(where("address", FilterOperator.IN_CIDR),
                "((source_address <<= :b::cidr) OR (destination_address <<= :b::cidr))");
    }

    @Test
    public void testNegativeEitherSideMatchesIfNeitherEndMatches() {
        // Not "source <> x AND destination <> x": that drops every session with an end that has no value.
        assertEquals(where("address_geo_asn_number", FilterOperator.NOT_EQUALS_NUMERIC),
                "(((source_address_geo_asn_number = :b) OR (destination_address_geo_asn_number = :b)) IS NOT TRUE)");
        assertEquals(where("address_geo_city", FilterOperator.NOT_EQUALS),
                "(((source_address_geo_city = :b) OR (destination_address_geo_city = :b)) IS NOT TRUE)");
        assertEquals(where("address", FilterOperator.NOT_IN_CIDR),
                "(((source_address <<= :b::cidr) OR (destination_address <<= :b::cidr)) IS NOT TRUE)");
    }

    @Test
    public void testNotPrivateMatchesIfEitherEndIsPublic() {
        String source = where("source_address", FilterOperator.IS_NOT_PRIVATE);
        String destination = where("destination_address", FilterOperator.IS_NOT_PRIVATE);
        assertEquals(where("address", FilterOperator.IS_NOT_PRIVATE), "((" + source + ") OR (" + destination + "))");
    }

    @Test
    public void testCgnatMatchesTheSharedAddressSpace() {
        assertEquals(where("source_address", FilterOperator.IS_CGNAT), "source_address <<= '100.64.0.0/10'::cidr");
        assertEquals(where("source_address", FilterOperator.IS_NOT_CGNAT),
                "NOT (source_address <<= '100.64.0.0/10'::cidr)");
        assertEquals(where("address", FilterOperator.IS_NOT_CGNAT), "(((source_address <<= '100.64.0.0/10'::cidr) "
                + "OR (destination_address <<= '100.64.0.0/10'::cidr)) IS NOT TRUE)");
    }

    @Test
    public void testPrivateIncludesUniqueLocalAddresses() {
        assertEquals(where("source_address", FilterOperator.IS_PRIVATE), "(source_address <<= '10.0.0.0/8'::cidr OR "
                + "source_address <<= '172.16.0.0/12'::cidr OR source_address <<= '192.168.0.0/16'::cidr OR "
                + "source_address <<= 'fc00::/7'::cidr)");
    }

    @Test
    public void testLinkLocalAndVersionOperators() {
        assertEquals(where("source_address", FilterOperator.IS_LINK_LOCAL),
                "(source_address <<= '169.254.0.0/16'::cidr OR source_address <<= 'fe80::/10'::cidr)");
        assertEquals(where("address", FilterOperator.IS_NOT_LINK_LOCAL),
                "((((source_address <<= '169.254.0.0/16'::cidr OR source_address <<= 'fe80::/10'::cidr)) OR "
                        + "((destination_address <<= '169.254.0.0/16'::cidr OR destination_address <<= 'fe80::/10'::cidr))) "
                        + "IS NOT TRUE)");
        assertEquals(where("source_address", FilterOperator.IS_IPV6), "family(source_address) = 6");
        assertEquals(where("ip_version", FilterOperator.EQUALS_NUMERIC), "family(source_address) = :b");
        assertEquals(where("address", FilterOperator.IS_IPV4),
                "((family(source_address) = 4) OR (family(destination_address) = 4))");
    }

    @Test
    public void testInternalFieldsReadTheStoredFlags() {
        assertEquals(where("source_is_internal", FilterOperator.BOOLEAN), "source_address_is_site_local = :b");
        assertEquals(where("destination_is_internal", FilterOperator.BOOLEAN),
                "destination_address_is_site_local = :b");
        assertEquals(where("is_internal", FilterOperator.BOOLEAN),
                "(source_address_is_site_local AND destination_address_is_site_local) = :b");
    }

    @Test
    public void testEitherSideUsesTheMatchersOfTheSidedFields() {
        L4Filters filters = new L4Filters();

        for (FilterOperator operator : new FilterOperator[]{FilterOperator.EQUALS, FilterOperator.REGEX_MATCH,
                FilterOperator.IN_CIDR, FilterOperator.IS_PRIVATE, FilterOperator.IS_CGNAT}) {
            String source = filters.buildSql("b", "source_address", operator).where();
            String destination = filters.buildSql("b", "destination_address", operator).where();
            assertEquals(where("address", operator), "((" + source + ") OR (" + destination + "))", operator.name());
        }
    }

    @Test
    public void testSidedFieldsAreUnchanged() {
        assertEquals(where("source_address_geo_asn_number", FilterOperator.NOT_EQUALS_NUMERIC),
                "source_address_geo_asn_number <> :b");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testUnknownFieldIsRejected() {
        new L4Filters().buildSql("b", "nope", FilterOperator.EQUALS);
    }

}
