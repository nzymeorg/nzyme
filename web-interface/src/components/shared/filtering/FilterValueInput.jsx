import React from "react";
import {FIELD_TYPE} from "./Filters";

export default function FilterValueInput(props) {

  const filterValue = props.filterValue;
  const onChange = props.onChange;
  const operator = props.operator;
  const filterType = props.filterType;
  const disabled = props.disabled;

  const fieldTypeAsInputType = () => {
    switch (operator.field_type) {
      case FIELD_TYPE.ANY_TEXT:
      case FIELD_TYPE.REGEX_TEXT:
      case FIELD_TYPE.CIDR:
      case FIELD_TYPE.NO_VALUE:
        return "text"
      case FIELD_TYPE.NUMBER:
        return "number"
      default:
        return "text"
    }
  }

  const placeHolder = () => {
    if (operator.field_type === FIELD_TYPE.NO_VALUE) {
      return null;
    }

    // A potential operator placeholder has priority over filter default value.
    if (operator.placeholder) {
      return operator.placeholder;
    } else if (filterType.placeholder) {
      return filterType.placeholder;
    } else {
      return null;
    }
  }

  switch (operator.field_type) {
    case FIELD_TYPE.SELECT:
      return (
        <select className="form-select"
                disabled={disabled}
                onChange={onChange}>
          <option selected>Please Choose</option>
          {operator.options.map((o, i) => {
            return <option key={i} value={o.value}>{o.name}</option>
          })}
        </select>
      )
    default:
      return (
        <input className="form-control" type={fieldTypeAsInputType()}
               disabled={disabled}
               value={filterValue}
               placeholder={placeHolder()}
               onChange={onChange} />
      )
  }

}