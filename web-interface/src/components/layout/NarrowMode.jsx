import AssetStylesheet from "../misc/AssetStylesheet";
import React from "react";

export default function NarrowMode({enabled}) {
    if (enabled) {
        return <AssetStylesheet filename="narrow.css" />
    } else {
        return null
    }
}