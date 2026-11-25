package app.nzyme.core.util.filters;

import app.nzyme.core.assets.AssetFilters;
import app.nzyme.core.ethernet.l4.filters.L4Filters;
import app.nzyme.core.ethernet.ssh.SSHFilters;
import app.nzyme.core.rest.parameters.FiltersParameter;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class AssetTagFilterTest {

    private static final String TAGGED = "SELECT tag_asset.mac FROM assets AS tag_asset "
            + "JOIN assets_tag_assignments AS tag_assignment ON tag_assignment.asset_id = tag_asset.id "
            + "JOIN assets_tags AS tag_definition ON tag_definition.id = tag_assignment.tag_id "
            + "WHERE tag_definition.uuid::text = lower(:b) "
            + "AND :asset_tag_scope_organization IN ('*', tag_definition.organization_id::text) "
            + "AND :asset_tag_scope_tenant IN ('*', tag_definition.tenant_id::text)";

    @Test
    public void theConditionOnlyRefersToTheMacAddress() {
        assertEquals(FilterSql.assetTagMatch("b", "source_mac", FilterOperator.EQUALS),
                "source_mac IN (" + TAGGED + ")");
    }

    @Test
    public void notCarryingATagIncludesWhatHasNoAddress() {
        assertEquals(FilterSql.assetTagMatch("b", "s.source_mac", FilterOperator.NOT_EQUALS),
                "(s.source_mac IS NULL OR s.source_mac NOT IN (" + TAGGED + "))");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void aTagIsCarriedOrNot() {
        FilterSql.assetTagMatch("b", "source_mac", FilterOperator.REGEX_MATCH);
    }

    @Test
    public void theTagFieldSitsWhereTheAddressFieldDoes() {
        GeneratedSql assets = new AssetFilters().buildSql("b", "tag", FilterOperator.EQUALS);
        assertEquals(assets.where(), "a.mac IN (" + TAGGED + ")");
        assertTrue(assets.having().isEmpty());

        GeneratedSql sessions = new L4Filters().buildSql("b", "destination_tag", FilterOperator.EQUALS);
        assertEquals(sessions.where(), "destination_mac IN (" + TAGGED + ")");

        GeneratedSql ssh = new SSHFilters().buildSql("b", "client_tag", FilterOperator.EQUALS);
        assertTrue(ssh.where().isEmpty());
        assertEquals(ssh.having(), "MIN(tcp.source_mac) IN (" + TAGGED + ")");
    }

    @Test
    public void theValueIsTheUuidOfTheTag() {
        UUID uuid = UUID.randomUUID();

        Filter has = FilterFactory.fromRestQuery(
                FiltersParameter.create("source_tag", "asset_tag_equals", uuid.toString().toUpperCase(), null));
        assertEquals(has.operator(), FilterOperator.EQUALS);
        assertEquals(has.value(), uuid.toString());

        Filter hasNot = FilterFactory.fromRestQuery(
                FiltersParameter.create("source_tag", "asset_tag_not_equals", uuid.toString(), null));
        assertEquals(hasNot.operator(), FilterOperator.NOT_EQUALS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void anythingButAUuidIsRefused() {
        FilterFactory.fromRestQuery(FiltersParameter.create("source_tag", "asset_tag_equals", "x' OR 1=1 --", null));
    }

    private static final String TAG = "0b4a3f0e-6a3c-4f0b-9a57-1d2c3e4f5a6b";

    private static Filters tagFilters(FilterScope scope) {
        return Filters.create(
                Map.of("source_tag", List.of(Filter.create("source_tag", FilterOperator.EQUALS, TAG, TAG))), scope);
    }

    @Test
    public void testAssetTagFilterIsLimitedToTheScope() {
        UUID organization = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        FilterSqlFragment sql = FilterSql.generate(tagFilters(FilterScope.tenant(organization, tenant)), new L4Filters());

        assertTrue(sql.whereSql().contains("tag_definition.organization_id"));
        assertTrue(sql.whereSql().contains("tag_definition.tenant_id"));
        assertEquals(sql.bindings().get("asset_tag_scope_organization"), organization.toString());
        assertEquals(sql.bindings().get("asset_tag_scope_tenant"), tenant.toString());
    }

    @Test
    public void testAssetTagFilterWithoutScopeMatchesNoTag() {
        FilterSqlFragment sql = FilterSql.generate(
                Filters.create(Map.of("source_tag", List.of(Filter.create("source_tag", FilterOperator.EQUALS, TAG, TAG)))),
                new L4Filters());

        assertEquals(sql.bindings().get("asset_tag_scope_organization"), FilterScope.none().organizationId());
        assertEquals(sql.bindings().get("asset_tag_scope_tenant"), FilterScope.none().tenantId());
        assertNotEquals(FilterScope.none().organizationId(), FilterScope.all().organizationId());
    }

    @Test
    public void testScopeWithoutOrganizationOrTenantIsNoScope() {
        assertEquals(FilterScope.tenant(null, UUID.randomUUID()), FilterScope.none());
        assertEquals(FilterScope.tenant(UUID.randomUUID(), null), FilterScope.none());
        assertEquals(FilterScope.organization(null), FilterScope.none());
    }

    @Test
    public void testParsedFiltersCarryTheirScope() {
        FilterScope scope = FilterScope.organization(UUID.randomUUID());

        assertEquals(FilterParser.parseFiltersQueryParameter(null, scope).scope(), scope);
        assertEquals(FilterParser.parseFiltersQueryParameter(
                "{\"source_tag\":[{\"field\":\"source_tag\",\"operator\":\"equals\",\"value\":\"" + TAG + "\"}]}",
                scope).scope(), scope);
    }

}
