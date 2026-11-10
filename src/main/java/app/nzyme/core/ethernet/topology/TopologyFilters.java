package app.nzyme.core.ethernet.topology;

import app.nzyme.core.ethernet.l4.filters.L4Filters;
import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.numericMatch;

// The topology aggregates session rows without grouping by session first, so duration is a row condition here.
public class TopologyFilters implements SqlFilterProvider {

    private final L4Filters l4Filters = new L4Filters();

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        if ("duration".equals(fieldName)) {
            return GeneratedSql.create(numericMatch(
                    bindId, "(EXTRACT(EPOCH FROM (most_recent_segment_time - start_time)) * 1000)", operator
            ), "");
        }

        return l4Filters.buildSql(bindId, fieldName, operator);
    }

}
