package app.nzyme.core.ethernet.ndp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class NdpFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "message_type":
                return GeneratedSql.create(stringMatch(bindId, "message_type", operator), "");
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "source_mac", operator), "");
            case "source_tag":
                return GeneratedSql.create(assetTagMatch(bindId, "source_mac", operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "destination_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "destination_address", operator), "");
            case "target_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "target_address", operator), "");
            case "source_link_layer_address":
                return GeneratedSql.create(macAddressMatch(bindId, "source_link_layer_address", operator), "");
            case "target_link_layer_address":
                return GeneratedSql.create(macAddressMatch(bindId, "target_link_layer_address", operator), "");
            case "hop_limit":
                return GeneratedSql.create(numericMatch(bindId, "hop_limit", operator), "");
            case "router":
                return GeneratedSql.create(booleanMatch(bindId, "router", operator), "");
            case "solicited":
                return GeneratedSql.create(booleanMatch(bindId, "solicited", operator), "");
            case "overrides":
                return GeneratedSql.create(booleanMatch(bindId, "overrides", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, "packet_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
