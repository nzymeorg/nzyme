import {OPERATORS} from "./Filters";

export default function reconstructFromNodeData(data, filterFields) {
  let result = {};

  for (let fieldName in data) {
    let fieldFilters = [];
    for (let fieldFilter of data[fieldName]) {
      let operator = OPERATORS[fieldFilter.operator];
      let filterDefinition = filterFields[fieldFilter.field];

      fieldFilters.push({
        name: filterDefinition.title,
        field: fieldFilter.field,
        operator: operator.name,
        sign: operator.sign,
        // Boolean filters are stored as booleans and are strings everywhere else.
        value: typeof fieldFilter.untransformed_value === "boolean"
            ? String(fieldFilter.untransformed_value) : fieldFilter.untransformed_value,
        transformed_value: fieldFilter.value
      });
    }

    result[fieldName] = fieldFilters;
  }

  return result
}