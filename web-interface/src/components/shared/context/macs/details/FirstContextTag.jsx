import React from "react";
import numeral from "numeral";
import {truncate} from "../../../../../util/Tools";

export default function FirstContextTag(props) {

  const tags = props.tags;

  if (!tags || tags.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  return (
      <span title={tags.map((tag) => tag.name).join(", ")}>
        {truncate(tags[0].name, 20)} ({numeral(tags.length).format("0,0")} total)
      </span>
  )

}
