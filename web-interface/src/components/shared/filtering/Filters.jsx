import React, {useContext, useEffect, useRef, useState} from 'react';
import AppliedFilterList from "./AppliedFilterList";
import FilterValueInput from "./FilterValueInput";
import validateStringNotEmpty from "./validators/StringNotEmptyValidator";
import validatePortNumberValid from "./validators/PortNumberValidator";
import validateDNSDataTypeValid from "./validators/DNSTypeValidator";
import validateCIDRValid from "./validators/CIDRValidator";
import {useNavigate} from "react-router-dom";
import validateIPAddressValid from "./validators/IPAddressValidator";
import validateMACAddressValid from "./validators/MACAddressValidator";
import validateNumber from "./validators/NumberValidator";
import {filtersToQueryParameters} from "./FilterQueryParameters";
import validateEnum from "./validators/EnumValidator";
import SaveFilterAsMonitorDialog from "./monitors/SaveFilterAsMonitorDialog";
import ApplyExistingMonitorDialog from "./monitors/ApplyExistingMonitorDialog";
import reconstructFromNodeData from "./FilterReconstructor";
import {TapContext} from "../../../App";
import Store from "../../../util/Store";
import useQuery from "../../../util/UseQuery";
import LoadingSpinner from "../../misc/LoadingSpinner";
import MonitorsService from "../../../services/MonitorsService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import WithPermission from "../../misc/WithPermission";
import requiredUserPermissionForMonitorWriteAccess from "../../monitors/shared/MonitorUserPermission";
import {
  ALLOWED_COMPLETION_STATUS_VALUES, COMPLETION_STATUS_SELECT_OPTIONS,
} from "./validators/CompletionStatusValidator";

export const FILTER_TYPE = {
  STRING: {
    name: "string",
    validators: [validateStringNotEmpty],
    placeholder: null
  },
  STRING_NO_REGEX: {
    name: "string_no_regex",
    validators: [validateStringNotEmpty],
    placeholder: null
  },
  UUID: {
    name: "uuid",
    validators: [validateStringNotEmpty],
    placeholder: null
  },
  STRING_ARRAY: {
    name: "stringarray",
    validators: [validateStringNotEmpty],
    placeholder: null
  },
  NUMERIC: {
    name: "numeric",
    validators: [validateNumber],
    placeholder: null
  },
  BOOLEAN: {
    name: "boolean",
    validators: [(value) => value === "true" || value === "false"],
    placeholder: null
  },
  IP_ADDRESS: {
    name: "ip_address",
    validators: [validateIPAddressValid],
    placeholder: "172.16.0.1"
  },
  MAC_ADDRESS: {
    name: "mac_address",
    validators: [validateMACAddressValid],
    placeholder: "00:00:00:00:00:00"
  },
  PORT_NUMBER: {
    name: "port_number",
    validators: [validatePortNumberValid],
    placeholder: null
  },
  DNS_TYPE: {
    name: "dns_type",
    validators: [validateDNSDataTypeValid],
    placeholder: "CNAME"
  },
  L4_SESSION_TYPE: {
    name: "l4_session_type",
    validators: [(value) => validateEnum(["TCP", "UDP"], value)],
    placeholder: "TCP"
  },
  L4_SESSION_STATE: {
    name: "l4_session_state",
    validators: [
        (value) => validateEnum(
            ["ACTIVE", "ESTABLISHED", "SYNSENT", "SYNRECEIVED", "FINWAIT1", "FINWAIT2", "CLOSED",
              "CLOSEDNODE", "CLOSEDFIN", "CLOSEDRST", "CLOSEDTIMEOUT", "CLOSEDTIMEOUTNODE", "REFUSED"],
            value,
            true
        )
    ],
    placeholder: "Established"
  },
  COMPLETION_STATUS: {
    name: "completion_status",
    validators: [
      (value) => validateEnum(ALLOWED_COMPLETION_STATUS_VALUES, value, true)
    ],
    placeholder: "Complete"
  }
}

export const FIELD_TYPE = {
  ANY_TEXT: "any_text",
  REGEX_TEXT: "regex_text",
  NUMBER: "any_number",
  CIDR: "cidr",
  SELECT: "select",
  NO_VALUE: "none"
}

export const OPERATORS = {
  EQUALS: {
    name: "equals",
    sign: "==",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.ANY_TEXT
  },
  NOT_EQUALS: {
    name: "not_equals",
    sign: "!=",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.ANY_TEXT
  },
  EQUALS_NUMERIC: {
    name: "equals_numeric",
    sign: "==",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.NUMBER
  },
  NOT_EQUALS_NUMERIC: {
    name: "not_equals_numeric",
    sign: "!=",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.NUMBER
  },
  REGEX_MATCH: {
    name: "regex_match",
    sign: "~=",
    placeholder: null,
    no_value: false,
    validators: [validateStringNotEmpty],
    field_type: FIELD_TYPE.REGEX_TEXT
  },
  NOT_REGEX_MATCH: {
    name: "not_regex_match",
    sign: "!~=",
    placeholder: null,
    no_value: false,
    validators: [validateStringNotEmpty],
    field_type: FIELD_TYPE.REGEX_TEXT
  },
  GREATER_THAN: {
    name: "greater_than",
    sign: ">",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.NUMBER
  },
  SMALLER_THAN: {
    name: "smaller_than",
    sign: "<",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.NUMBER
  },
  IN_CIDR: {
    name: "in_cidr",
    sign: "IN CIDR:",
    placeholder: "172.16.0.0/24",
    no_value: false,
    validators: [validateCIDRValid],
    field_type: FIELD_TYPE.CIDR
  },
  NOT_IN_CIDR: {
    name: "not_in_cidr",
    sign: "NOT IN CIDR:",
    placeholder: "172.16.0.0/24",
    no_value: false,
    validators: [validateCIDRValid],
    field_type: FIELD_TYPE.CIDR
  },
  IS_PRIVATE: {
    name: "is_private",
    sign: "IS PRIVATE",
    placeholder: null,
    no_value: true,
    validators: [() => { return true; }],
    field_type: FIELD_TYPE.NO_VALUE
  },
  IS_NOT_PRIVATE: {
    name: "is_not_private",
    sign: "IS NOT PRIVATE",
    placeholder: null,
    no_value: true,
    validators: [() => { return true; }],
    field_type: FIELD_TYPE.NO_VALUE
  },
  CONTAINS: {
    name: "contains",
    sign: "Contains",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.ANY_TEXT
  },
  NOT_CONTAINS: {
    name: "not_contains",
    sign: "Does not contain",
    placeholder: null,
    no_value: false,
    validators: [],
    field_type: FIELD_TYPE.ANY_TEXT
  },
  IS_EMPTY: {
    name: "is_empty",
    sign: "Is empty",
    placeholder: null,
    no_value: true,
    validators: [() => { return true; }],
    field_type: FIELD_TYPE.NO_VALUE
  },
  IS_NOT_EMPTY: {
    name: "is_not_empty",
    sign: "Is not empty",
    placeholder: null,
    no_value: true,
    validators: [() => { return true; }],
    field_type: FIELD_TYPE.NO_VALUE
  },
  BOOLEAN: {
    name: "boolean",
    sign: "==",
    placeholder: null,
    no_value: false,
    options: [
      {name: "True", value: "true"},
      {name: "False", value: "false"}
    ],
    validators: [(value) => value === "true" || value === "false"],
    field_type: FIELD_TYPE.SELECT
  },
  COMPLETION_STATUS_EQUALS: {
    name: "completion_status_equals",
    sign: "==",
    placeholder: null,
    no_value: false,
    validators: [],
    options: COMPLETION_STATUS_SELECT_OPTIONS,
    field_type: FIELD_TYPE.SELECT
  },
  COMPLETION_STATUS_NOT_EQUALS: {
    name: "completion_status_not_equals",
    sign: "!=",
    placeholder: null,
    no_value: false,
    validators: [],
    options: COMPLETION_STATUS_SELECT_OPTIONS,
    field_type: FIELD_TYPE.SELECT
  },
}

const monitorsService = new MonitorsService();

export default function Filters(props) {

  const [organizationId, tenantId] = useSelectedTenant();
  const navigate = useNavigate();
  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();

  const fields = props.fields;
  const filters = props.filters ? props.filters : {};
  const setFilters = props.setFilters;

  const previousFiltersRef = useRef(null);

  const hideAppliedFilters = props.hideAppliedFilters === undefined || props.hideAppliedFilters === null ? false : props.hideAppliedFilters;
  const preSelectedField = props.preSelectedField;
  const preSelectedValue = props.preSelectedValue;

  const onSaveAsMonitor = props.onSaveAsMonitor;
  const monitorType = props.monitorType;
  const onMonitorsReady = props.onMonitorsReady;
  const [showSaveAsMonitorDialog, setShowSaveAsMonitorDialog] = useState(false);
  const [showApplyExistingMonitorDialog, setShowApplyExistingMonitorDialog] = useState(false);
  const [showUrlAppliedFilterHint, setShowUrlAppliedFilterHint] = useState(false);

  const [urlRequestedMonitor, setUrlRequestedMonitor] = useState(urlQuery.get("monitor"));
  const [appliedMonitor, setAppliedMonitor] = useState(null);
  const [showMonitorClearedHint, setShowMonitorClearedHint] = useState(false);

  const defaultOperator = OPERATORS.EQUALS;
  const defaultFilter = { name: "", field: "0", type: FILTER_TYPE.STRING, value_transform: null };

  const [selectionMade, setSelectionMade] = useState(false);
  const [selectedFilter, setSelectedFilter] = useState(defaultFilter);
  const [allowedOperators, setAllowedOperators] = useState([defaultOperator]);
  const [selectedOperator, setSelectedOperator] = useState(defaultOperator);
  const [filterValue, setFilterValue] = useState("");
  const [validatorsFailed, setValidatorsFailed] = useState(false);

  const onFilterSelected = (e) => {
    e.preventDefault();

    const selectedOption = e.target.options[e.target.selectedIndex];
    const filterOption = fields[selectedOption.value];

    if (filterOption) {
      changeFilter(filterOption.title, selectedOption.value, filterOption.type, filterOption.value_transform);
    } else {
      // The option dialog placeholder was selected again.
      changeFilter(defaultFilter.name, defaultFilter.field, defaultFilter.type, null);
    }
  }

  const changeFilter = (name, field, type, valueTransform) => {
    setFilterValue("");
    setSelectedFilter({ name: name, field: field, type: type, value_transform: valueTransform});

    if (field === "0") {
      // Reset.
      setSelectionMade(false);
      setSelectedOperator(defaultOperator);
    } else {
      setSelectionMade(true);
    }

    // Set options allowed by filter.
    if (fields[field]) {
      switch (fields[field].type) {
        case FILTER_TYPE.STRING:
        case FILTER_TYPE.DNS_TYPE:
        case FILTER_TYPE.L4_SESSION_STATE:
        case FILTER_TYPE.L4_SESSION_TYPE:
        case FILTER_TYPE.MAC_ADDRESS:
          setAllowedOperators([
            OPERATORS.EQUALS,
            OPERATORS.NOT_EQUALS,
            OPERATORS.REGEX_MATCH,
            OPERATORS.NOT_REGEX_MATCH
          ]);
          break;
        case FILTER_TYPE.STRING_NO_REGEX:
        case FILTER_TYPE.UUID:
          setAllowedOperators([
            OPERATORS.EQUALS,
            OPERATORS.NOT_EQUALS
          ]);
          break;
        case FILTER_TYPE.NUMERIC:
        case FILTER_TYPE.PORT_NUMBER:
          setAllowedOperators([
            OPERATORS.EQUALS_NUMERIC,
            OPERATORS.NOT_EQUALS_NUMERIC,
            OPERATORS.GREATER_THAN,
            OPERATORS.SMALLER_THAN
          ]);
          break;
        case FILTER_TYPE.BOOLEAN:
          setAllowedOperators([
            OPERATORS.BOOLEAN
          ]);
          break;
        case FILTER_TYPE.IP_ADDRESS:
          setAllowedOperators([
            OPERATORS.EQUALS,
            OPERATORS.NOT_EQUALS,
            OPERATORS.REGEX_MATCH,
            OPERATORS.NOT_REGEX_MATCH,
            OPERATORS.IN_CIDR,
            OPERATORS.NOT_IN_CIDR,
            OPERATORS.IS_PRIVATE,
            OPERATORS.IS_NOT_PRIVATE
          ]);
          break;
        case FILTER_TYPE.STRING_ARRAY:
          setAllowedOperators([
              OPERATORS.CONTAINS,
              OPERATORS.NOT_CONTAINS,
              OPERATORS.IS_EMPTY,
              OPERATORS.IS_NOT_EMPTY
          ])
          break;
        case FILTER_TYPE.COMPLETION_STATUS:
          setAllowedOperators([
            OPERATORS.COMPLETION_STATUS_EQUALS,
            OPERATORS.COMPLETION_STATUS_NOT_EQUALS
          ])
      }
    }

    validate();
  }

  useEffect(() => {
    // Select first allowed operator if allowed operators change (after filter selection)
    if (allowedOperators.length > 0) {
      setSelectedOperator(allowedOperators[0]);
    }
  }, [allowedOperators]);

  const onOperatorSelected = (e) => {
    e.preventDefault();

    const selectedOperator = OPERATORS[e.target.value.toUpperCase()];

    if (selectedOperator.no_value) {
      setFilterValue("");
    }

    setSelectedOperator(selectedOperator);
  }

  const onFilterValueChanged = (e) => {
    e.preventDefault();
    setFilterValue(e.target.value);
  }

  useEffect(() => {
    validate();
  }, [filterValue, selectedOperator]);

  useEffect(() => {
    if (preSelectedField && preSelectedValue != null) {
      changeFilter(fields[preSelectedField].title, preSelectedField, fields[preSelectedField].type, fields[preSelectedField].value_transform);
      setFilterValue(preSelectedValue);
    }
  }, [preSelectedField, preSelectedValue]);

  // Store filters in URL query param.
  useEffect(() => {
    const queryParams = new URLSearchParams(window.location.search);
    const currentFilters = queryParams.get("filters");

    // Stringify the filters object for comparison
    const filtersString = JSON.stringify(filters);

    if (previousFiltersRef.current !== filtersString && !appliedMonitor) {
      previousFiltersRef.current = filtersString;
      if (currentFilters !== filtersString) {
        queryParams.set("filters", JSON.stringify(filtersToQueryParameters(filters)));
        navigate({ search: queryParams.toString() });
      }
    }
  }, [filters, navigate]);

  // Store monitor that was loaded by user in URL query param.
  useEffect(() => {
    if (appliedMonitor) {
      const queryParams = new URLSearchParams(window.location.search);
      queryParams.set("monitor", appliedMonitor.uuid);
      queryParams.delete("filters");
      navigate({ search: queryParams.toString() });
    }
  }, [appliedMonitor])

  // Load monitor that was requested via URL query param.
  useEffect(() => {
    if (!preSelectedField && urlRequestedMonitor && (!appliedMonitor || urlRequestedMonitor !== appliedMonitor.uuid)) {
      monitorsService.findOne(urlRequestedMonitor, organizationId, tenantId, applyMonitor)
      setShowUrlAppliedFilterHint(true);
    } else {
      if (onMonitorsReady) {
        onMonitorsReady();
      }
    }
  }, [urlRequestedMonitor])

  const validate = () => {
    // Potential operator validators have priority over filter validators.
    let validators;
    if (selectedOperator.validators.length > 0) {
      validators = selectedOperator.validators;
    } else if (selectedFilter.type.validators.length > 0) {
      validators = selectedFilter.type.validators;
    } else {
      validators = null;
    }

    let failed = false;
    for (const validator of validators) {
      if (!validator(filterValue)) {
        failed = true;
        break;
      }
    }

    setValidatorsFailed(failed);
  }

  const onFilterAdded = (e) => {
    e.preventDefault();

    const addedFilter = {
      name: selectedFilter.name,
      field: selectedFilter.field,
      operator: selectedOperator.name,
      sign: selectedOperator.sign,
      value: (typeof filterValue === 'string' || filterValue instanceof String) ? filterValue.trim() : filterValue,
      transformed_value: selectedFilter.value_transform ? selectedFilter.value_transform(filterValue) : null
    }
    
    // Check if this filter already exists and do not add again if so.
    for (const filterName of Object.keys(filters)) {
      const filterList = filters[filterName];

      for (const existingFilter of filterList) {
        if (existingFilter.field === addedFilter.field
            && existingFilter.operator === addedFilter.operator
            && existingFilter.value === addedFilter.value) {
          return;
        }
      }
    }

    const newFilters = JSON.parse(JSON.stringify(filters)); // TODO wft (this is a way to make sure we create a completely new object so useState notices the change. Refactor all of this goddamn)
    if (filters[selectedFilter.field]) {
      // We already have a filter for this field. Add to list of filter values. (Will be OR-connected in backend)
      newFilters[selectedFilter.field].push(addedFilter);
    } else {
      // First filter for this field.
      newFilters[selectedFilter.field] = [addedFilter];
    }

    setFilters(newFilters);
    setFilterValue("");
  }

  const onFilterRemoved = (e, deleteFilter) => {
    e.preventDefault();

    const newFilters = {};

    // Add all existing filters except the one to be removed.
    for (const filterName of Object.keys(filters)) {
      const filterList = filters[filterName];

      for (const existingFilter of filterList) {
        if (existingFilter.field !== deleteFilter.field
            || existingFilter.operator !== deleteFilter.operator
            || existingFilter.value !== deleteFilter.value) {

          if (newFilters[filterName]) {
            newFilters[filterName].push(existingFilter);
          } else {
            newFilters[filterName] = [existingFilter];
          }
        }
      }
    }

    setFilters(newFilters);
  }

  const toggleSaveAsMonitor = () => {
    setShowSaveAsMonitorDialog(!showSaveAsMonitorDialog);
  }

  const toggleApplyExistingMonitor = () => {
    setShowApplyExistingMonitorDialog(!showApplyExistingMonitorDialog);
  }

  const applyExistingMonitorDialog = () => {
    if (!showApplyExistingMonitorDialog) {
      return null;
    }

    return <ApplyExistingMonitorDialog monitorType={monitorType}
                                       onApply={(monitor) => { applyMonitor(monitor); toggleApplyExistingMonitor(); }}
                                       onClose={toggleApplyExistingMonitor} />
  }

  const saveAsMonitorDialog = () => {
    if (!showSaveAsMonitorDialog) {
      return null;
    }

    return <SaveFilterAsMonitorDialog filters={filters}
                                      appliedMonitor={appliedMonitor}
                                      onClose={toggleSaveAsMonitor}
                                      onSave={onSaveAsMonitor}  />
  }

  const applyMonitor = (monitor) => {
    setFilters(reconstructFromNodeData(JSON.parse(monitor.filters), fields));

    let newTaps = monitor.taps;
    if (monitor.taps === null) {
      newTaps = "*";
    }

    Store.set("selected_taps", newTaps);
    tapContext.set(newTaps);

    setAppliedMonitor(monitor);
    if (onMonitorsReady) {
      onMonitorsReady();
    }
  }

  const clearMonitor = (e) => {
    e.preventDefault();

    setAppliedMonitor(null);
    setUrlRequestedMonitor(null);
    setFilters(null);

    const queryParams = new URLSearchParams(window.location.search);
    queryParams.delete("monitor");
    navigate({ search: queryParams.toString() });

    setShowMonitorClearedHint(true);
  }

  if (!preSelectedField && (urlRequestedMonitor && !appliedMonitor)) {
    return (
      <LoadingSpinner/>
    )
  }

  return (
      <div className="filters">
        <div className="input-group">
          <select className="form-select form-select-sm" value={selectedFilter.field}
                  onChange={onFilterSelected}>
            <option value="0">Select a Field</option>

            {Object.keys(fields).map((field, i) => {
              return <option value={field} key={i}>{fields[field].title}</option>
            })}
          </select>

          <select className="form-select form-select-sm" value={selectedOperator.name}
                  onChange={onOperatorSelected} disabled={!selectionMade}>
            {Object.keys(allowedOperators).map((op, i) => {
              return <option value={allowedOperators[op].name} key={i}>{allowedOperators[op].sign}</option>
            })}
          </select>

          <FilterValueInput filterValue={filterValue}
                            operator={selectedOperator}
                            filterType={selectedFilter.type}
                            onChange={onFilterValueChanged}
                            disabled={!selectionMade || (selectionMade && selectedOperator.no_value)} />

          <button className="btn btn-primary" type="button"
                  disabled={validatorsFailed || selectedFilter.field === "0"}
                  onClick={onFilterAdded}>
            Add Filter
          </button>

          { onSaveAsMonitor && monitorType ?
            <>
              <WithPermission permission={requiredUserPermissionForMonitorWriteAccess(monitorType)}>
                <button className="btn btn-outline-secondary" type="button"
                        disabled={Object.keys(filters).length === 0}
                        onClick={toggleSaveAsMonitor}>
                  { appliedMonitor ? "Save Monitor" : "Save as new Monitor" }
                </button>
              </WithPermission>
              <button className="btn btn-outline-secondary" type="button" onClick={toggleApplyExistingMonitor}>
                Load Existing Monitor
              </button>

              {applyExistingMonitorDialog()}
              {saveAsMonitorDialog()}
            </> : null }
        </div>

        {!hideAppliedFilters && <AppliedFilterList filters={filters}
                                                   appliedMonitor={appliedMonitor}
                                                   onFilterRemoved={onFilterRemoved} />}


        { appliedMonitor ?
          <a href="#" className="btn btn-outline-secondary btn-sm mt-2" onClick={clearMonitor}>Clear Monitor</a> : null }
        { showMonitorClearedHint ?
          <div className="alert alert-info alert-dismissible mt-2 mb-0">
            Monitor cleared. Tap selection may have changed. Please verify.

            <button type="button" className="btn-close" onClick={(e) => {
              e.preventDefault();
              setShowMonitorClearedHint(false)
            }}></button>
          </div> : null}
        { (appliedMonitor && appliedMonitor.partial_data) ?
          <div className="alert alert-warning mt-2 mb-0">
            At least one tap of the loaded monitor is not accessible by you. You may see partial
            data. Please check the monitor configuration.
          </div> : null }

        { showUrlAppliedFilterHint ?
          <div className="alert alert-info alert-dismissible mt-2 mb-0">
            Monitor loaded. Tap selection may have changed. Please verify.

            <button type="button" className="btn-close" onClick={(e) => {
              e.preventDefault();
              setShowUrlAppliedFilterHint(false)
            }}></button>
          </div> : null}
      </div>
  )

}