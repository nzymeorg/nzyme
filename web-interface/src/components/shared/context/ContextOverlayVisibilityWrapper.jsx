import React from "react";

function ContextOverlayVisibilityWrapper(props) {

  const visible = props.visible;
  const overlay = props.overlay;
  const fit = props.fit;
  const wide = props.wide;

  if (!visible) {
    return null;
  }

  return <div className={"context-overlay" + (fit ? " context-overlay-fit" : "") + (wide ? " context-overlay-wide" : "")}>{overlay}</div>;

}

export default ContextOverlayVisibilityWrapper;