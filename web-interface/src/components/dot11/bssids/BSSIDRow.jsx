import React, {useContext, useState} from "react";
import moment from "moment";
import numeral from "numeral";
import SSIDsList from "../util/SSIDsList";
import SignalStrength from "../../shared/SignalStrength";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import BSSIDDetailsRows from "./BSSIDDetailsRows";
import BSSIDSecurityProtocols from "./BSSIDSecurityProtocols";
import InfrastructureTypes from "../util/InfrastructureTypes";
import Dot11MacAddress from "../../shared/context/macs/Dot11MacAddress";
import {BSSID_FILTER_FIELDS} from "./BssidFilterFields";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {translateInfrastructureType} from "../util/InfrastructureTypeTranslator";

const dot11Service = new Dot11Service();

function BSSIDRow(props) {
  const tapContext = useContext(TapContext);

  const bssid = props.bssid;
  const timeRange = props.timeRange;
  const setFilters = props.setFilters;

  const [ssids, setSSIDs] = useState(null);
  const [ssidsLoading, setSSIDsLoading] = useState(false);

  const selectedTaps = tapContext.taps;

  const onExpandClick = function(e, bssid) {
    e.preventDefault();

    if (ssidsLoading) {
      // Don't do anything if already loading.
      return;
    }

    if (ssids === null) {
      setSSIDsLoading(true);
      dot11Service.findSSIDsOfBSSID(bssid, timeRange, selectedTaps, function(ssids) {
        setSSIDs(ssids);
        setSSIDsLoading(false);
      });
    } else {
      setSSIDs(null);
    }
  }

  return (
      <React.Fragment>
        <tr>
          <td>
            <Dot11MacAddress addressWithContext={bssid.bssid}
                             withAssetName
                             filterElement={<FilterValueIcon setFilters={setFilters}
                                                             fields={BSSID_FILTER_FIELDS}
                                                             field="bssid"
                                                             value={bssid.bssid.address} />}
                             onClick={(e) => onExpandClick(e, bssid.bssid.address)} />
          </td>
          <td>
            <SignalStrength strength={bssid.signal_strength_average} selectedTapCount={selectedTaps === "*" ? null : selectedTaps.length} />
            <FilterValueIcon setFilters={setFilters}
                             fields={BSSID_FILTER_FIELDS}
                             field="signal_strength"
                             value={Math.round(bssid.signal_strength_average)} />
          </td>
          <td>
            <InfrastructureTypes types={bssid.infrastructure_types} setFilters={setFilters} />
          </td>
          <td>
            { bssid.has_hidden_ssid_advertisements || bssid.advertised_ssid_names.length === 0 ? <span className="text-muted">&lt;hidden&gt;</span> : null }{ bssid.has_hidden_ssid_advertisements && bssid.advertised_ssid_names.length > 0 ? ", " : null }
            <SSIDsList ssids={bssid.advertised_ssid_names} setFilters={setFilters} filterFields={BSSID_FILTER_FIELDS} filterFieldName="advertised_ssid" />
          </td>
          <td>
            {numeral(bssid.client_count).format("0,0")}
            <FilterValueIcon setFilters={setFilters}
                             fields={BSSID_FILTER_FIELDS}
                             field="client_count"
                             value={bssid.client_count} />
          </td>
          <td><BSSIDSecurityProtocols bssid={bssid} setFilters={setFilters} /></td>
          <td>{bssid.bssid.oui ? bssid.bssid.oui : <span className="text-muted">Unknown</span>}</td>
          <td title={moment(bssid.last_seen).format()}>
            {moment(bssid.last_seen).fromNow()}
          </td>
        </tr>
        <BSSIDDetailsRows bssid={bssid.bssid} ssids={ssids} loading={ssidsLoading} />
      </React.Fragment>
  )

}

export default BSSIDRow;