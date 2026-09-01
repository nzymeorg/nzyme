import MacAddress from "./MacAddress";
import React from "react";
import BluetoothMacAddressContextOverlay from "./details/BluetoothMacAddressContextOverlay";

function BluetoothMacAddress(props) {

  const address = props.address;
  const addressWithContext = props.addressWithContext;
  const type = props.type;
  const showOui = props.showOui;
  const highlighted = props.highlighted;

  // Optional.
  const href = props.href;
  const onClick = props.onClick;
  const filterElement = props.filterElement;

  return (
      <span className="mac-address">
        <MacAddress address={address}
                    addressWithContext={addressWithContext}
                    overlay={<BluetoothMacAddressContextOverlay
                        address={addressWithContext ? addressWithContext.address : address}
                        isRandomized={addressWithContext ? addressWithContext.is_randomized : address.is_randomized} />}
                    type={type}
                    showOui={showOui}
                    filterElement={filterElement}
                    href={href}
                    onClick={onClick}
                    highlighted={highlighted} />
      </span>
  )

}

export default BluetoothMacAddress;