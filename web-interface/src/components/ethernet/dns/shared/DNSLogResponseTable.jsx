import React, {useContext, useEffect, useState} from "react";
import moment from "moment";
import DNSData from "../DNSData";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import ETLD from "../../shared/ETLD";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import DNSService from "../../../../services/ethernet/DNSService";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

const COLSPAN = 8;

const dnsService = new DNSService();

export default function DNSLogResponseTable(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const transactionId = props.transactionId;
  const transactionTimestamp = props.transactionTimestamp;
  const show = props.show;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [responses, setResponses] = useState(null);

  useEffect(() => {
    if (show && !responses) {
      dnsService.findResponsesOfTransaction(organizationId, tenantId, transactionId, transactionTimestamp, selectedTaps, setResponses);
    }
  }, [show, organizationId, tenantId, transactionId, selectedTaps]);

  if (!show) {
    return null;
  }

  if (responses == null) {
    return (
      <tr>
        <td colSpan={COLSPAN} style={{paddingLeft: 20, paddingRight: 20}}>
          <LoadingSpinner />
        </td>
      </tr>
    )
  }

  if (responses.length === 0) {
    return (
        <tr>
          <td colSpan={COLSPAN}>No responses recorded.</td>
        </tr>
    )
  }

  return (
      <tr>
        <td colSpan={COLSPAN} style={{paddingLeft: 20, paddingRight: 20}}>
          <table className="table table-sm table-hover table-striped mb-1 mt-0">
            <thead>
            <tr>
              <th>Response Type</th>
              <th>Response Value</th>
              <th>Response eTLD</th>
              <th>Response Code</th>
              <th>Answered By</th>
              <th>Time</th>
            </tr>
            </thead>
            <tbody>
            {responses.map((response, i) => {
              return (
                  <tr key={i}>
                    <td>{response.data_type}</td>
                    <td title={response.data_value}><DNSData value={response.data_value}/></td>
                    <td title={response.data_value_etld}><ETLD etld={response.data_value_etld}/></td>
                    <td>{response.response_code ? response.response_code : <span className="text-muted">n/a</span>}</td>
                    <td>
                      {response.server && response.server.mac ?
                        <EthernetMacAddress addressWithContext={response.server.mac} withAssetLink withAssetName />
                        : <span className="text-muted">Unknown</span>}
                    </td>
                    <td title={moment(response.timestamp).format()}>{moment(response.timestamp).format("HH:mm:ss.SSS")}</td>
                  </tr>
              )
            })}
            </tbody>
          </table>

        </td>
      </tr>
  )

}