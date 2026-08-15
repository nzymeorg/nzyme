import React from "react";
import L4Address from "./L4Address";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";

export default function L4AddressList({addresses, setFilters, fields, field, count = 1}) {
  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  const shown = addresses.slice(0, count);
  const remaining = addresses.length - shown.length;

  return (
    <>
      {shown.map((address, i) => {
        const isLast = i === shown.length - 1;
        return (
          <L4Address key={i}
                     address={address}
                     filterElement={address ? <FilterValueIcon setFilters={setFilters}
                                                               fields={fields}
                                                               field={field}
                                                               value={address.address} /> : null }
                     suffixElement={isLast && remaining > 0 ? (
                       <span className="text-muted">&nbsp;+ {remaining} more</span>
                     ) : null} />
        );
      })}
    </>
  )
}