import React from "react";
import {POLICY_STATES} from "../SegmentationFormat";

const STYLES = {
  HOLDING: {className: "text-success"},
  CROSSED: {className: "text-danger"},
  ATTEMPTED: {className: "text-warning"},
  NOT_OBSERVABLE: {className: "text-muted", icon: "fa-solid fa-eye-slash"},
  PAUSED: {className: "text-muted"}
};

export default function PolicyState({state}) {

  const known = POLICY_STATES[state];
  const style = STYLES[state];

  if (!known || !style) {
    return <span className="text-nowrap">{state}</span>
  }

  return (
    <span className={"text-nowrap " + style.className} title={known.description}>
      {style.icon ? <React.Fragment><i className={style.icon}></i>{' '}</React.Fragment> : null}
      {known.title}
    </span>
  )

}
