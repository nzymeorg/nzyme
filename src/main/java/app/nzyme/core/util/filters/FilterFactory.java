package app.nzyme.core.util.filters;

import app.nzyme.core.rest.parameters.FiltersParameter;

public class FilterFactory {

    /*
     * This logic mostly just maps the String filter operator names to their corresponding Enum value and
     * casts the filter value to what the operator works with. For example, numeric value filters cast the
     * value to Long, while others keep it as String.
     *
     * This fails hard in case of unexpected filter configurations because the UI takes care of a lot of
     * filter validation. No invalid filters should ever arrive here.
     */

    public static Filter fromRestQuery(FiltersParameter parameter) {
        switch (parameter.operator().toLowerCase()) {
            case "equals":
            case "completion_status_equals":
            case "active_status_equals":
            case "l4_connection_type_equals":
                return Filter.create(
                        parameter.field(), FilterOperator.EQUALS, optionallyTransformedValue(parameter), parameter.value()
                );
            case "not_equals":
            case "completion_status_not_equals":
            case "active_status_not_equals":
            case "l4_connection_type_not_equals":
                return Filter.create(
                        parameter.field(), FilterOperator.NOT_EQUALS, optionallyTransformedValue(parameter), parameter.value()
                );
            case "equals_numeric":
                return Filter.create(
                        parameter.field(), FilterOperator.EQUALS_NUMERIC, Long.valueOf(optionallyTransformedValue(parameter)), Long.valueOf(parameter.value())
                );
            case "not_equals_numeric":
                return Filter.create(
                        parameter.field(), FilterOperator.NOT_EQUALS_NUMERIC, Long.valueOf(optionallyTransformedValue(parameter)), Long.valueOf(parameter.value())
                );
            case "regex_match":
                return Filter.create(
                        parameter.field(), FilterOperator.REGEX_MATCH, optionallyTransformedValue(parameter), parameter.value()
                );
            case "not_regex_match":
                return Filter.create(
                        parameter.field(), FilterOperator.NOT_REGEX_MATCH, optionallyTransformedValue(parameter), parameter.value()
                );
            case "starts_with":
                return Filter.create(
                        parameter.field(), FilterOperator.STARTS_WITH, optionallyTransformedValue(parameter), parameter.value()
                );
            case "ends_with":
                return Filter.create(
                        parameter.field(), FilterOperator.ENDS_WITH, optionallyTransformedValue(parameter), parameter.value()
                );
            case "length_equals":
                return Filter.create(
                        parameter.field(), FilterOperator.LENGTH_EQUALS, optionallyTransformedValue(parameter), parameter.value()
                );
            case "length_greater_than":
                return Filter.create(
                        parameter.field(), FilterOperator.LENGTH_GREATER_THAN, optionallyTransformedValue(parameter), parameter.value()
                );
            case "length_smaller_than":
                return Filter.create(
                        parameter.field(), FilterOperator.LENGTH_SMALLER_THAN, optionallyTransformedValue(parameter), parameter.value()
                );
            case "greater_than":
                return Filter.create(
                        parameter.field(), FilterOperator.GREATER_THAN, Long.valueOf(optionallyTransformedValue(parameter)), Long.valueOf(parameter.value())
                );
            case "smaller_than":
                return Filter.create(
                        parameter.field(), FilterOperator.SMALLER_THAN, Long.valueOf(optionallyTransformedValue(parameter)), Long.valueOf(parameter.value())
                );
            case "in_cidr":
                return Filter.create(
                        parameter.field(), FilterOperator.IN_CIDR, optionallyTransformedValue(parameter), parameter.value()
                );
            case "not_in_cidr":
                return Filter.create(
                        parameter.field(), FilterOperator.NOT_IN_CIDR, optionallyTransformedValue(parameter), parameter.value()
                );
            case "is_private":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_PRIVATE, optionallyTransformedValue(parameter), parameter.value()
                );
            case "is_not_private":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_NOT_PRIVATE, optionallyTransformedValue(parameter), parameter.value()
                );
            case "contains":
                return Filter.create(
                        parameter.field(), FilterOperator.CONTAINS, optionallyTransformedValue(parameter), parameter.value()
                );
            case "not_contains":
                return Filter.create(
                        parameter.field(), FilterOperator.NOT_CONTAINS, optionallyTransformedValue(parameter), parameter.value()
                );
            case "is_empty":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_EMPTY, optionallyTransformedValue(parameter), parameter.value()
                );
            case "is_not_empty":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_NOT_EMPTY, optionallyTransformedValue(parameter), parameter.value()
                );
            case "boolean":
                return Filter.create(
                        parameter.field(), FilterOperator.BOOLEAN, Boolean.valueOf(optionallyTransformedValue(parameter)), Boolean.valueOf(parameter.value())
                );
            case "is_null":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_NULL, parameter.value(), parameter.value()
                );
            case "is_not_null":
                return Filter.create(
                        parameter.field(), FilterOperator.IS_NOT_NULL, parameter.value(), parameter.value()
                );
            default:
                throw new RuntimeException("Unknown filter operator: [" + parameter.operator() + "]");
        }
    }

    private static String optionallyTransformedValue(FiltersParameter parameter) {
        if (parameter.transformedValue() != null && !parameter.transformedValue().isEmpty()) {
            return parameter.transformedValue();
        } else {
            return parameter.value();
        }
    }

}
