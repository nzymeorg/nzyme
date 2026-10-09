import React from "react";
import Consequence from "../Consequence";
import NodeFilePermissionsProcedure from "./procedures/NodeFilePermissionsProcedure";

function NodeFilePermissionsConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Node File Permissions"
          color="red"
          problem="One or more nodes report files holding secrets that are accessible by other users on the node host. This includes the cluster PGP private key, a TLS private key or the configuration file with the database password."
          acceptableRange={[
            "n/a"
          ]}
          consequences={[
            "Other users on the node host can decrypt every encrypted value in the database, including tap secrets and TLS keys",
            "The node will refuse to start on its next restart until permissions are fixed"
          ]}
          procedure={<NodeFilePermissionsProcedure />}
      />
  )

}

export default NodeFilePermissionsConsequence;
