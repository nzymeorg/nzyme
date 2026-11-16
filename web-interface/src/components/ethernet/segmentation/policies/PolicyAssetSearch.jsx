import React, {useState} from "react";
import AssetsService from "../../../../services/ethernet/AssetsService";
import {Presets} from "../../../shared/timerange/TimeRange";
import {isValidMACAddress, isValidUUID} from "../../../../util/Tools";
import validateIPAddressValid from "../../../shared/filtering/validators/IPAddressValidator";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import AssetIpAddresses from "../../assets/AssetIpAddresses";
import {rememberAssetName} from "./PolicyAssetLabel";

const assetsService = new AssetsService();

const MAX_RESULTS = 10;

const filtersOf = (text) => {
  if (isValidMACAddress(text)) {
    return {mac: [{field: "mac", operator: "equals", value: text.toUpperCase()}]};
  }

  if (validateIPAddressValid(text)) {
    return {ip_address: [{field: "ip_address", operator: "equals", value: text}]};
  }

  return {hostname: [{field: "hostname", operator: "regex_match", value: text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}]};
}

export default function PolicyAssetSearch({organizationId, tenantId, onSelect, idPrefix}) {

  const [text, setText] = useState("");
  const [assets, setAssets] = useState(null);
  const [searching, setSearching] = useState(false);

  const search = () => {
    const query = text.trim();
    if (query === "") {
      return;
    }

    if (isValidUUID(query)) {
      setText("");
      setAssets(null);
      onSelect(query.toLowerCase());
      return;
    }

    setSearching(true);
    assetsService.findAllAssets(organizationId, tenantId, Presets.ALL_TIME, "last_seen", "DESC", filtersOf(query),
      MAX_RESULTS, 0, (found) => {
        setSearching(false);
        setAssets(found);
      });
  }

  const select = (e, asset) => {
    e.preventDefault();

    rememberAssetName(asset);
    setText("");
    setAssets(null);
    onSelect(asset.uuid);
  }

  const results = () => {
    if (!assets) {
      return null;
    }

    if (assets.assets.length === 0) {
      return <div className="form-text">No asset found.</div>
    }

    return (
      <table className="table table-sm table-hover mt-2 mb-0">
        <tbody>
        {assets.assets.map((asset) => {
          return (
            <tr key={asset.uuid}>
              <td><EthernetMacAddress addressWithContext={asset.mac} /></td>
              <td>
                {asset.name ? <span className="context-name context-name-asset">{asset.name}</span>
                  : (asset.hostnames && asset.hostnames.length > 0 ? asset.hostnames[0] : <span className="text-muted">No name</span>)}
              </td>
              <td><AssetIpAddresses addresses={asset.ip_addresses} /></td>
              <td className="text-end"><a href="#" onClick={(e) => select(e, asset)}>Add</a></td>
            </tr>
          )
        })}
        {assets.total > assets.assets.length ?
          <tr><td colSpan={4} className="text-muted">and {assets.total - assets.assets.length} more</td></tr>
          : null}
        </tbody>
      </table>
    )
  }

  return (
    <React.Fragment>
      <div className="input-group input-group-sm">
        <input type="text" className="form-control" id={idPrefix + "-asset"} autoComplete="off"
               aria-label="IP address, MAC address or hostname of the asset"
               placeholder="IP address, MAC address or hostname"
               value={text} onChange={(e) => setText(e.target.value)}
               onKeyDown={(e) => {
                 if (e.key === "Enter") {
                   e.preventDefault();
                   search();
                 }
               }} />
        <button className="btn btn-outline-secondary" type="button" disabled={text.trim() === "" || searching}
                onClick={search}>
          {searching ? <i className="fa-solid fa-circle-notch fa-spin"></i> : "Find Asset"}
        </button>
      </div>

      {results()}
    </React.Fragment>
  )

}
