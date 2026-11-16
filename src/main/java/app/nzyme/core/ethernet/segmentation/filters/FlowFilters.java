package app.nzyme.core.ethernet.segmentation.filters;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class FlowFilters implements SqlFilterProvider {

    public static final String SESSIONS = "(sessions + continued)";

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "destination_address", operator), "");
            case "source_asn":
                return GeneratedSql.create(numericMatch(bindId, "source_asn", operator), "");
            case "destination_asn":
                return GeneratedSql.create(numericMatch(bindId, "destination_asn", operator), "");
            case "source_country_code":
                return GeneratedSql.create(stringMatch(bindId, "source_country_code", operator), "");
            case "destination_country_code":
                return GeneratedSql.create(stringMatch(bindId, "destination_country_code", operator), "");
            case "l4_type":
                return GeneratedSql.create(stringMatch(bindId, "l4_type", operator), "");
            case "port":
                return GeneratedSql.create(numericMatch(bindId, "destination_port", operator), "");
            case "protocol":
                return GeneratedSql.create(stringMatch(bindId, "protocol", operator), "");
            case "outcome":
                return GeneratedSql.create(stringMatch(bindId, "outcome", operator), "");
            case "sessions":
                return GeneratedSql.create("", numericMatch(bindId, SESSIONS, operator));
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
