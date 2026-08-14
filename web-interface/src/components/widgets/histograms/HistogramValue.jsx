import React from "react";

import numeral from "numeral";
import AutomaticDot11MacAddressLink from "../../shared/context/macs/AutomaticDot11MacAddressLink";
import ApiRoutes from "../../../util/ApiRoutes";
import L4Address from "../../ethernet/shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {ARP_FILTER_FIELDS} from "../../ethernet/assets/arp/ARPFilterFields";
import moment from "moment";
import AssetHostnames from "../../ethernet/assets/AssetHostnames";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {MODE_TABLE} from "./HistogramModes";
import InternalAddressOnlyWrapper from "../../ethernet/shared/InternalAddressOnlyWrapper";

function HistogramValue(props) {

  const highlightValue = props.highlightValue;
  const value = props.value;
  const filterElement = props.filterElement;

  switch (value.type) {
    case "DOT11_MAC":
      if (value.metadata && value.metadata.type) {
        return (
            <>
              <AutomaticDot11MacAddressLink
              highlighted={value.value === highlightValue}
              mac={value.value}
              type={value.metadata.type}
              addressWithContext={value.metadata.mac} />

              {filterElement}
            </>
        )
      } else {
        return <span className={value.value === highlightValue ? "highlighted" : null}>{value.value} {filterElement}</span>
      }
    case "ETHERNET_MAC":
      if (value.metadata) {
        return <EthernetMacAddress addressWithContext={value.metadata} filterElement={filterElement} withAssetLink withAssetName />
      } else {
        return "[missing metadata]"
      }
    case "ETHERNET_MAC_NO_INTERNAL":
      if (value.metadata) {
        return (
          <EthernetMacAddress addressWithContext={value.metadata} filterElement={filterElement} withAssetLink withAssetName />
        )
      } else {
        // If no MAC provided, it's an internal address. Show the default "internal address" placeholder.
        return <InternalAddressOnlyWrapper address="127.0.0.1" />
      }
    case "L4_ADDRESS":
      return <L4Address address={value.value} filterElement={filterElement} />
    case "L4_ADDRESS_NO_PORT":
      return <L4Address address={value.value} hidePort={true} filterElement={filterElement} />
    case "L4_PORT":
      return <><span className="machine-data">{value.value}</span> {filterElement}</>
    case "BYTES":
      return <>{numeral(value.value).format("0,0b")} {filterElement}</>
    case "DNS_TRANSACTION_LOG_LINK":
      return <a href={ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS + "?filters=" + value.metadata.filter_parameters}>{numeral(value.value.title).format("0,0")}</a>
    case "INTEGER":
      return <span className={value.value === highlightValue ? "highlighted" : null}>{numeral(value.value).format("0,0")} {filterElement}</span>
    case "DOUBLE_DECIMAL2":
      return <span className={value.value === highlightValue ? "highlighted" : null}>{numeral(value.value).format("0,0.00")} {filterElement}</span>
    case "GENERIC":
      return <span className={value.value === highlightValue ? "highlighted" : null}>{value.value} {filterElement}</span>
    case "DATETIME":
      return <span className={value.value === highlightValue ? "highlighted" : null} title={moment(value.value).format()}>
        {moment(value.value).fromNow()}
      </span>
    case "ASSET_HOSTNAMES":
      return <AssetHostnames hostnames={value.value} />
    default:
      return <span>[unknown value type]</span>
  }

}

export default HistogramValue;