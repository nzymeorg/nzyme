import React, {useContext, useEffect, useState} from "react";
import moment from "moment";
import Dot11Service from "../../../services/Dot11Service";
import ApiRoutes from "../../../util/ApiRoutes";
import {TapContext} from "../../../App";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11MacAddress from "../../shared/context/macs/Dot11MacAddress";
import PivotLink from "../../shared/PivotLink";
import accessPointsOfSsidLink from "../monitoring/ssid/accessPointsOfSsidLink";

const dot11Service = new Dot11Service();

export default function BSSIDSiblings({bssid, timeRange, withoutSsidOnly}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const tapsKey = Array.isArray(selectedTaps) ? selectedTaps.join(",") : selectedTaps;

  const [siblings, setSiblings] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let current = true;

    setSiblings(null);
    setFailed(false);
    dot11Service.findSiblingsOfBSSID(bssid, timeRange, withoutSsidOnly, selectedTaps, (response) => {
      if (current) {
        setSiblings(response);
      }
    }, () => {
      if (current) {
        setFailed(true);
      }
    });

    return () => { current = false; }
  }, [bssid, timeRange, withoutSsidOnly, tapsKey]);

  if (failed) {
    return <div className="alert alert-warning mb-0">Could not look up BSSIDs. Try a shorter time range.</div>
  }

  if (!siblings) {
    return <LoadingSpinner />
  }

  if (siblings.siblings.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          {withoutSsidOnly ? "No related BSSIDs without SSID found." : "No other BSSIDs of this device found."}
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped mb-0">
          <thead>
          <tr>
            <th>BSSID</th>
            {withoutSsidOnly ? null : <th title="Up to 5 SSIDs per BSSID">Advertised SSIDs</th>}
            <th>Last Seen</th>
          </tr>
          </thead>
          <tbody>
          {siblings.siblings.map((sibling) => {
            return (
                <tr key={sibling.bssid.address}>
                  <td className="text-nowrap">
                    <Dot11MacAddress addressWithContext={sibling.bssid}
                                     href={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(sibling.bssid.address)} />
                    <PivotLink to={ApiRoutes.DOT11.NETWORKS.BSSIDS.TIMELIME(sibling.bssid.address)}
                               title="Timeline of this BSSID"
                               className="ms-1 small"
                               icon={true} />
                  </td>
                  {withoutSsidOnly ? null :
                      <td className="text-break">
                        {sibling.ssids.length === 0
                            ? <span className="text-muted">&lt;hidden&gt;</span> : sibling.ssids.map((ssid, i) => (
                                <span key={ssid}>
                                  {i > 0 ? ", " : null}
                                  {ssid}
                                  <PivotLink to={accessPointsOfSsidLink(ssid, sibling.last_seen)}
                                             title="Access points that advertise this SSID"
                                             className="ms-1 small"
                                             icon={true} />
                                </span>
                            ))}
                      </td>}
                  <td className="text-nowrap" title={moment(sibling.last_seen).format()}>
                    {moment(sibling.last_seen).fromNow()}
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        {siblings.limited ?
            <p className="text-muted mt-2 mb-0">The list is limited to the 50 BSSIDs seen last.</p> : null}
      </React.Fragment>
  )

}
