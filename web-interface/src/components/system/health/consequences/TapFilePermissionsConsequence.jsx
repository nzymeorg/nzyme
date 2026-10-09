import React from "react";
import Consequence from "../Consequence";
import TapFilePermissionsProcedure from "./procedures/TapFilePermissionsProcedure";

function TapFilePermissionsConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Tap File Permissions"
          color="red"
          problem="One or more taps report files that are accessible by other users on the tap host. The tap configuration file contains the tap secret."
          acceptableRange={[
            "n/a"
          ]}
          consequences={[
            "Other users on the tap host can read the tap secret and impersonate the tap",
            "The tap will refuse to start on its next restart until permissions are fixed"
          ]}
          procedure={<TapFilePermissionsProcedure />}
      />
  )

}

export default TapFilePermissionsConsequence;
