import React from "react";
import {formatCount} from "../assets/details/AssetProfileHelpers";

export default function TopologyCoverageNote({shown, next = undefined, onShowMore = undefined}) {

  if (!shown) {
    return null;
  }

  return (
      <div className="text-muted small mt-1">
        {shown.text}
        {next !== undefined ?
            <button type="button" className="btn btn-link p-0 ms-1 align-baseline" style={{fontSize: "inherit"}}
                    onClick={() => onShowMore(next)}>
              Show top {formatCount(next)}
            </button> : null}
      </div>
  )

}
