import React from "react";

function ContextOverlayVisibilityWrapper(props) {

  const visible = props.visible;
  const overlay = props.overlay;
  const fit = props.fit;

  if (!visible) {
    return null;
  }

  return <div className={"context-overlay" + (fit ? " context-overlay-fit" : "")}>{overlay}</div>;

}

export default ContextOverlayVisibilityWrapper;