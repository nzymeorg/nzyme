import React from "react";
import VizMenu from "./VizMenu";

export default function VizSelectionActions({filters = [], pivots = []}) {

  if (filters.length === 0 && pivots.length === 0) {
    return null;
  }

  return (
      <div className="d-flex border-top p-2" style={{gap: "6px"}}>
        {filters.length > 0 ? <VizMenu label="Filter" items={filters} block={true} up={true} /> : null}
        {pivots.length > 0 ? <VizMenu label="Pivot" items={pivots} block={true} up={true} /> : null}
      </div>
  )

}
