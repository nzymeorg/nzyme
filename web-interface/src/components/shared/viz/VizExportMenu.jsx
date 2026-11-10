import React from "react";
import VizMenu from "./VizMenu";

export default function VizExportMenu({items, note = null}) {

  return <VizMenu label="Export" items={items} note={note} />;

}
