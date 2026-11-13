import React from "react";

const SIZES = {SMALL: "dashboard-text-small", MEDIUM: "", LARGE: "dashboard-text-large"};

export default function TextWidget({text, size}) {

  return <div className={"dashboard-text " + (SIZES[size] ? SIZES[size] : "")}>{text}</div>

}
