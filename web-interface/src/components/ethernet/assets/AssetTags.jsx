import React from "react";
import {truncate} from "../../../util/Tools";

export default function AssetTags({tags, filterElement = undefined}) {

  const additional = () => {
    if (tags.length < 2) {
      return null
    }

    return <span>(+{tags.length-1})</span>
  }

  if (!tags || tags.length === 0) {
    return <span className="text-muted">None</span>;
  }

  return (
      <span title={tags.map((tag) => tag.name).join(", ")}>
        <i className="fa-solid fa-tag text-muted"></i> {truncate(tags[0].name, 24, false)}{' '}{additional()}{filterElement}
      </span>
  )

}
