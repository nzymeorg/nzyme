import React from "react";
import L4Address from "./L4Address";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";

export default function L4AddressList({addresses, setFilters, fields, field, count = 1, asList = false}) {
  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">None</span>
  }

  const shown = addresses.slice(0, count);
  const remaining = addresses.length - shown.length;

  const element = (address, i) => {
    const isLast = i === shown.length - 1;

    return <L4Address key={i}
               address={address}
               filterElement={address && setFilters ? <FilterValueIcon setFilters={setFilters}
                                                                       fields={fields}
                                                                       field={field}
                                                                       value={address.address} /> : null }
               suffixElement={isLast && remaining > 0 ? (
                 asList ? <li>+ {remaining} more</li> : <span className="text-muted">&nbsp;+ {remaining} more</span>
               ) : null} />
  }

  if (asList) {
    return (
      <ul style={{listStyleType: "none"}} className="m-0 p-0">
        {shown.map((address, i) => {
          return <li>{element(address, i)}</li>
        })}
      </ul>
    )
  }

  return (
    <>
      {shown.map((address, i) => {
        return element(address, i);
      })}
    </>
  )
}