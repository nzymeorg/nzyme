package app.nzyme.core.util.filters;

import app.nzyme.core.rest.parameters.FiltersParameter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.util.List;
import java.util.Map;

public class FilterParser {

    private static final Logger LOG = LogManager.getLogger(FilterParser.class);

    public static final ObjectMapper OM = JsonMapper.builder()
            .addModule(new JodaModule())
            .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Filters parseFiltersQueryParameter(String query, FilterScope scope) {
        if (query == null || query.isEmpty()) {
            return Filters.create(Maps.newHashMap(), scope);
        }

        Map<String, List<FiltersParameter>> param;
        try {
            param = OM.readValue(query, new TypeReference<>() {});
        } catch (JacksonException e) {
            LOG.error(e);
            throw new IllegalArgumentException("Invalid filters parameter provided.", e);
        }

        Map<String, List<Filter>> filters = Maps.newHashMap();
        for (Map.Entry<String, List<FiltersParameter>> filterParam : param.entrySet()) {
            try {
                List<Filter> fieldFilters = Lists.newArrayList();
                for (FiltersParameter filter : filterParam.getValue()) {
                    fieldFilters.add(FilterFactory.fromRestQuery(filter));
                }
                filters.put(filterParam.getKey(), fieldFilters);
            } catch (Exception e) {
                LOG.error(e);
                throw new IllegalArgumentException("Invalid filters parameter provided.", e);
            }
        }

        return Filters.create(filters, scope);
    }

}
