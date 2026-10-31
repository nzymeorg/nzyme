import React from "react";
import Consequence from "../Consequence";
import ConnectProcedure from "./procedures/ConnectProcedure";

function ConnectConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Connect"
          color="red"
          problem="Nzyme Connect is enabled but the cluster has not been able to submit a status report for more than two minutes."
          acceptableRange={[
            "A status report is accepted by Connect at least every two minutes while Connect is enabled"
          ]}
          consequences={[
            "Data provided by Connect, such as MAC address vendors, Bluetooth device information, GeoIP and ASN " +
            "data, UAV models and environmental data, is no longer updated.",
            "Connect does not receive status reports of this cluster."
          ]}
          procedure={<ConnectProcedure />}
      />
  )

}

export default ConnectConsequence;
