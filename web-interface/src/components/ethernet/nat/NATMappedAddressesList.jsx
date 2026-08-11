import React from "react";
import L4Address from "../shared/L4Address";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS} from "./traversal/NATTraversalDiscoveryFilterFields";

export default function NATMappedAddressesList({addresses, setFilters}) {

  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  const first = addresses[0];
  const remaining = addresses.length - 1;

  return (
    <>
      <L4Address address={first}
                 filterElement={first ? <FilterValueIcon setFilters={setFilters}
                                                         fields={NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS}
                                                         field="mapped_address"
                                                         value={first.address} /> : null }
                 suffixElement={remaining > 0 && (
                   <span className="text-muted">&nbsp;+ {remaining} more</span>
                 )} />
    </>
  )

}