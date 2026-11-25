package app.nzyme.core.ethernet.ndp;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

public class NdpRouterAdvertisementFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "source_mac":
                return GeneratedSql.create(macAddressMatch(bindId, NDP.ROUTER_MAC, operator), "");
            case "source_tag":
                return GeneratedSql.create(assetTagMatch(bindId, NDP.ROUTER_MAC, operator), "");
            case "source_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "source_address", operator), "");
            case "hop_limit":
                return GeneratedSql.create(numericMatch(bindId, "hop_limit", operator), "");
            case "managed":
                return GeneratedSql.create(booleanMatch(bindId, "managed", operator), "");
            case "other_configuration":
                return GeneratedSql.create(booleanMatch(bindId, "other_configuration", operator), "");
            case "preference":
                return GeneratedSql.create(stringMatch(bindId, "preference", operator), "");
            case "router_lifetime":
                return GeneratedSql.create(numericMatch(bindId, "router_lifetime", operator), "");
            case "packet_count":
                return GeneratedSql.create(numericMatch(bindId, "packet_count", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

}
