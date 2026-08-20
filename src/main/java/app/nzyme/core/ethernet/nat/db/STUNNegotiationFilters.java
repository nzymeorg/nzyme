package app.nzyme.core.ethernet.nat.db;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;
import static app.nzyme.core.util.filters.FilterSql.ipAddressMatch;
import static app.nzyme.core.util.filters.FilterSql.jsonbNestedFieldMatchAny;

public class STUNNegotiationFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "successful":
                return GeneratedSql.create("", booleanMatch(bindId, "BOOL_OR(successful)", operator));
            case "active":
                return GeneratedSql.create("", booleanMatch(bindId, "(MAX(s.most_recent_segment_time) >= NOW() - INTERVAL '60 seconds')", operator));
            case "is_turn":
                return GeneratedSql.create(booleanMatch(bindId, "is_turn", operator), "");
            case "negotiation_key_sha256":
                return GeneratedSql.create(stringMatch(bindId, "negotiation_key_sha256", operator),"");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "source_mac", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "destination_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "destination_mac", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "destination_address", operator), "");
            case "bytes_exchanged":
                return GeneratedSql.create("", numericMatch(bindId, "MAX(s.bytes_rx_count+s.bytes_tx_count)", operator));
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
