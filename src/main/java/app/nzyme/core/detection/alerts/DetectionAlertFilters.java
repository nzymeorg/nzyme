package app.nzyme.core.detection.alerts;

import app.nzyme.core.util.filters.FilterOperator;
import app.nzyme.core.util.filters.GeneratedSql;
import app.nzyme.core.util.filters.SqlFilterProvider;

import static app.nzyme.core.util.filters.FilterSql.*;

/*
 * Filters apply to the "alerts" CTE of DetectionAlertService.searchCte(), which carries the computed status,
 * affected address, affected MAC and re-activation count next to the plain alert columns.
 */
public class DetectionAlertFilters implements SqlFilterProvider {

    @Override
    public GeneratedSql buildSql(String bindId, String fieldName, FilterOperator operator) {
        switch (fieldName) {
            case "status":
                return GeneratedSql.create(stringMatch(bindId, "alerts.status", operator), "");
            case "type":
                return GeneratedSql.create(stringMatch(bindId, "alerts.detection_type", operator), "");
            case "subsystem":
                return GeneratedSql.create(stringMatch(bindId, "alerts.subsystem", operator), "");
            case "details":
                return GeneratedSql.create(stringMatch(bindId, "alerts.details", operator), "");
            case "affected_address":
                return GeneratedSql.create(ipAddressMatch(bindId, "alerts.affected_address", operator), "");
            case "affected_mac":
                return GeneratedSql.create(macAddressMatch(bindId, "alerts.affected_mac", operator), "");
            case "attribute":
                return GeneratedSql.create(anyAttributeMatch(bindId, operator), "");
            case "reactivations":
                return GeneratedSql.create(numericMatch(bindId, "alerts.reactivations", operator), "");
            default:
                throw new RuntimeException("Unknown field name [" + fieldName + "].");
        }
    }

    /*
     * Matches against the value of any attribute of the alert. Negative operators are expressed as the absence of
     * a positively matching attribute, so "not equals X" means no attribute of the alert has the value X.
     */
    private static String anyAttributeMatch(String bindId, FilterOperator operator) {
        String exists = "EXISTS (SELECT 1 FROM detection_alert_attributes AS aa " +
                "WHERE aa.detection_alert_id = alerts.id AND ";

        switch (operator) {
            case EQUALS:
                return exists + "aa.attribute_value = :" + bindId + ")";
            case NOT_EQUALS:
                return "NOT " + exists + "aa.attribute_value = :" + bindId + ")";
            case REGEX_MATCH:
                return exists + "aa.attribute_value ~ :" + bindId + ")";
            case NOT_REGEX_MATCH:
                return "NOT " + exists + "aa.attribute_value ~ :" + bindId + ")";
            case CONTAINS:
                return exists + "strpos(aa.attribute_value, :" + bindId + ") > 0)";
            case NOT_CONTAINS:
                return "NOT " + exists + "strpos(aa.attribute_value, :" + bindId + ") > 0)";
            case STARTS_WITH:
                return exists + "left(aa.attribute_value, char_length(:" + bindId + ")) = :" + bindId + ")";
            case ENDS_WITH:
                return exists + "right(aa.attribute_value, char_length(:" + bindId + ")) = :" + bindId + ")";
            case LENGTH_EQUALS:
                return exists + "char_length(aa.attribute_value) = :" + bindId + "::int)";
            case LENGTH_GREATER_THAN:
                return exists + "char_length(aa.attribute_value) > :" + bindId + "::int)";
            case LENGTH_SMALLER_THAN:
                return exists + "char_length(aa.attribute_value) < :" + bindId + "::int)";
            case IS_NULL:
                return "NOT " + exists + "1=1)";
            case IS_NOT_NULL:
                return exists + "1=1)";
            default:
                throw new RuntimeException("Invalid operator [" + operator + "] for attribute filter.");
        }
    }

}
