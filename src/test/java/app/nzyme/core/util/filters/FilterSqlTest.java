package app.nzyme.core.util.filters;

import app.nzyme.core.ethernet.l4.filters.L4Filters;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class FilterSqlTest {

    private static Filters filters(String field, FilterOperator operator, Object value) {
        return Filters.create(Map.of(field, List.of(Filter.create(field, operator, value, value))));
    }

    @Test
    public void testKnownFieldGeneratesSql() {
        FilterSqlFragment sql = FilterSql.generate(
                filters("destination_port", FilterOperator.EQUALS_NUMERIC, 443L), new L4Filters());

        assertTrue(sql.whereSql().contains("destination_port"));
        assertEquals(sql.bindings().size(), 1);
    }

    @Test
    public void testUnknownFieldIsAnInvalidArgument() {
        assertThrows(IllegalArgumentException.class, () -> FilterSql.generate(
                filters("no_such_field", FilterOperator.EQUALS, "x"), new L4Filters()));
    }

    @Test
    public void testOperatorTheFieldDoesNotTakeIsAnInvalidArgument() {
        assertThrows(IllegalArgumentException.class, () -> FilterSql.generate(
                filters("destination_port", FilterOperator.REGEX_MATCH, "x"), new L4Filters()));
    }

}
