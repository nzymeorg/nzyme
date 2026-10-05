import React from "react";

export default function AssetName(props) {

  const addressWithContext = props.addressWithContext;

  if (!addressWithContext || !addressWithContext.context || !addressWithContext.context.name || addressWithContext.context.name.trim() === "") {
    return <span className="text-muted">None</span>
  }

  return <span className="context-name context-name-asset">{addressWithContext.context.name}</span>;

}