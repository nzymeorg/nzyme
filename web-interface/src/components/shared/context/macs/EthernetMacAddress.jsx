import React from "react";
import MacAddress from "./MacAddress";
import EthernetMacAddressContextOverlay from "./details/EthernetMacAddressContextOverlay";

export default function EthernetMacAddress(props) {

  const address = props.address;
  const addressWithContext = props.addressWithContext;
  const showOui = props.showOui;
  const highlighted = props.highlighted;

  // Optional.
  const href = props.href;
  const withAssetLink = props.withAssetLink;
  const withAssetName = props.withAssetName;
  const onClick = props.onClick;
  const filterElement = props.filterElement;
  const suffixElement = props.suffixElement;
  const hideActiveIndicator = props.hideActiveIndicator;

  return (
      <span className="mac-address">
        <MacAddress address={address}
                    addressWithContext={addressWithContext}
                    overlay={<EthernetMacAddressContextOverlay
                        assetId={addressWithContext ? addressWithContext.asset_id : null}
                        address={addressWithContext ? addressWithContext.address : address}
                        oui={addressWithContext ? addressWithContext.oui : null} />}
                    showOui={showOui}
                    href={href}
                    onClick={onClick}
                    filterElement={filterElement}
                    suffixElement={suffixElement}
                    highlighted={highlighted}
                    hideActiveIndicator={hideActiveIndicator}
                    withAssetLink={withAssetLink}
                    withAssetName={withAssetName} />
      </span>
  )

}