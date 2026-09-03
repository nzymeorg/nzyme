import React from 'react';
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../bluetooth/BluetoothDevicesFilterFields";
import FilterValueIcon from "./filtering/FilterValueIcon";

export default function GroupedParameterList({list, valueTransform, setFilters = undefined, fields = undefined, fieldName = undefined}) {

  const formatValue = (value) => {
    if (valueTransform) {
      return valueTransform(value);
    } else {
      return value;
    }
  }

  const filterIcon = (value) => {
    if (!setFilters || !fieldName || !fields) {
      return null;
    }

    return (
      <FilterValueIcon setFilters={setFilters}
                       fields={BLUETOOTH_DEVICES_FILTER_FIELDS}
                       field={fieldName}
                       value={value} />
    )
  }

  if (!list || list.length === 0 || list[0] == null) {
    return <span className="text-muted">n/a</span>
  }

  return (
      <React.Fragment>
        {list.map((x, i) => {
          return (
              <React.Fragment key={i}>
                {formatValue(x)}{i < list.length-1 ? <span>{filterIcon(x)}, </span> : <span>{filterIcon(x)}</span>}

              </React.Fragment>
          )
        })}
      </React.Fragment>
  )

}