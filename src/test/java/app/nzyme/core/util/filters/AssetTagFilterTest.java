package app.nzyme.core.util.filters;

import app.nzyme.core.assets.AssetFilters;
import app.nzyme.core.ethernet.l4.filters.L4Filters;
import app.nzyme.core.ethernet.ssh.SSHFilters;
import app.nzyme.core.rest.parameters.FiltersParameter;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class AssetTagFilterTest {

    private static final String TAGGED = "SELECT tag_asset.mac FROM assets AS tag_asset "
            + "JOIN assets_tag_assignments AS tag_assignment ON tag_assignment.asset_id = tag_asset.id "
            + "JOIN assets_tags AS tag_definition ON tag_definition.id = tag_assignment.tag_id "
            + "WHERE tag_definition.uuid::text = lower(:b)";

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

}
