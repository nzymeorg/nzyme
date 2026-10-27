import React from "react";

export default function AssetActiveIndicator({active, hideText, filterElement}) {

  if (active == null) {
    return <span title="Cannot decide if asset is active or not because I have no asset information.">
      <i className="fa fa-circle text-muted"></i> {hideText ? null : "Unknown"}</span>
  }

  if (active) {
    return <span title="Asset is active. It was seen in the previous 30 minutes.">
      <i className="fa fa-circle text-success"></i> {hideText ? null : "Active"} {filterElement ? filterElement : null}</span>
  } else {
    return <span title="Asset is not active. It was not seen in the previous 30 minutes.">
      <i className="fa fa-circle text-danger"></i> {hideText ? null : "Inactive"} {filterElement ? filterElement : null}</span>
  }

}