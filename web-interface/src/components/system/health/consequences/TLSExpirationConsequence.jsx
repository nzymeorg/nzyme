import React from "react";
import Consequence from "../Consequence";
import TLSExpirationProcedure from "./procedures/TLSExpirationProcedure";

function TLSExpirationConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="TLS Expiration"
          color="red"
          problem="At least one Nzyme node is using a TLS certificate that is about to expire or has expired."
          acceptableRange={[
            "Certificate expiration must be more than 7 days in the future"
          ]}
          consequences={[
            "Users of Nzyme web interface will receive a warning about the expired certificate and have to manually " +
            "accept it after the certificate expired.",
            "Depending on configuration, taps will refuse to connect and send data after the certificate expired."
          ]}
          procedure={<TLSExpirationProcedure />}
      />
  )

}

export default TLSExpirationConsequence;