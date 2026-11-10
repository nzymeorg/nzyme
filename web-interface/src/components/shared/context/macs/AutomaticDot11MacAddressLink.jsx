import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import Dot11MacAddress from "./Dot11MacAddress";
import {ACCESS_POINT, CLIENT} from "./Dot11MacAddressType";

function AutomaticDot11MacAddressLink(props) {

  const address = props.mac;
  const addressWithContext = props.addressWithContext;
  const type = props.type;
  const highlighted = props.highlighted;
  const filterElement = props.filterElement;

  const link = (bssid, type) => {
    switch (type) {
      case ACCESS_POINT:
        return ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(address);
      case CLIENT:
        return ApiRoutes.DOT11.CLIENTS.DETAILS(address);
    }
  }

  if (type === ACCESS_POINT || type === CLIENT) {
    return (
        <span className="mac-address">
          <Dot11MacAddress address={address}
                      addressWithContext={addressWithContext}
                      type={type}
                      href={link(address, type)}
                      highlighted={highlighted}
                      filterElement={filterElement} />
        </span>
    )
  } else {
    return <Dot11MacAddress address={address}
                            addressWithContext={addressWithContext}
                            type={type}
                            highlighted={highlighted}
                            filterElement={filterElement} />
  }

}

export default AutomaticDot11MacAddressLink;