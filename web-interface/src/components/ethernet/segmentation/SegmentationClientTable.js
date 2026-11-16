import {FIELD_TYPE, OPERATORS} from "../../shared/filtering/Filters";

/*
 * A filter means what it means on the node, where text is compared as it is written, with one exception: the
 * operators that say "not" match everything their counterpart does not, also rows that hold nothing in the field.
 */

const isEmpty = (value) => value === null || value === undefined || value === "";

const text = (value) => (isEmpty(value) ? "" : String(value));

const regexMatches = (value, pattern) => {
  try {
    return new RegExp(pattern).test(text(value));
  } catch {
    return false;
  }
}

const numberMatches = (value, wanted, compare) => !isEmpty(value) && compare(Number(value), Number(wanted));

const MATCHERS = {
  equals: (value, wanted) => text(value) === wanted,
  not_equals: (value, wanted) => text(value) !== wanted,
  regex_match: (value, wanted) => regexMatches(value, wanted),
  not_regex_match: (value, wanted) => !regexMatches(value, wanted),
  contains: (value, wanted) => text(value).includes(wanted),
  not_contains: (value, wanted) => !text(value).includes(wanted),
  starts_with: (value, wanted) => text(value).startsWith(wanted),
  ends_with: (value, wanted) => text(value).endsWith(wanted),
  length_equals: (value, wanted) => text(value).length === Number(wanted),
  length_greater_than: (value, wanted) => text(value).length > Number(wanted),
  length_smaller_than: (value, wanted) => text(value).length < Number(wanted),
  equals_numeric: (value, wanted) => numberMatches(value, wanted, (a, b) => a === b),
  not_equals_numeric: (value, wanted) => !numberMatches(value, wanted, (a, b) => a === b),
  greater_than: (value, wanted) => numberMatches(value, wanted, (a, b) => a > b),
  smaller_than: (value, wanted) => numberMatches(value, wanted, (a, b) => a < b),
  is_null: (value) => isEmpty(value),
  is_not_null: (value) => !isEmpty(value)
};

const listOperator = (name) => {
  return Object.values(OPERATORS).find((operator) => operator.name === name && operator.field_type === FIELD_TYPE.SELECT);
}

const filterMatches = (value, filter) => {
  const wanted = isEmpty(filter.value) ? "" : String(filter.value);

  const matcher = MATCHERS[filter.operator];
  if (matcher) {
    return matcher(value, wanted);
  }

  const picked = listOperator(filter.operator);
  if (picked) {
    return picked.sign === "!=" ? text(value) !== wanted : text(value) === wanted;
  }

  // An operator that cannot be evaluated here must not look like it let everything through.
  return false;
}

export const rowMatches = (row, filters, fields) => {
  return Object.keys(filters || {}).every((name) => {
    const field = fields[name];
    if (!field || !field.value || filters[name].length === 0) {
      return true;
    }

    const value = field.value(row);
    return filters[name].some((filter) => filterMatches(value, filter));
  });
}

export const pageWithin = (page, total, perPage) => {
  return Math.min(Math.max(page, 1), Math.max(Math.ceil(total / perPage), 1));
}

export const pageRows = (rows, page, perPage) => {
  return rows.slice((page - 1) * perPage, page * perPage);
}
