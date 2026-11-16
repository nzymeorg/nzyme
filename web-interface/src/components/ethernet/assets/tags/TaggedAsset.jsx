import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function TaggedAsset({asset}) {

  return (
    <span className="mac-address">
      <Link to={ApiRoutes.ETHERNET.ASSETS.DETAILS(asset.uuid)}>{asset.mac}</Link>
      {asset.name ?
        <span className="context-name context-name-asset" style={{marginLeft: 5}}>{asset.name}</span> : null}
    </span>
  )

}
