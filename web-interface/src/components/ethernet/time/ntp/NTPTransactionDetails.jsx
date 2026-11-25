import TimeService from "../../../../services/ethernet/TimeService";
import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import {formatSubMicro} from "../../../../util/Tools";
import moment from "moment";
import UDPSessionLink from "../../shared/UDPSessionLink";

const timeService = new TimeService();

export default function NTPTransactionDetails({ transactionId }) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [transaction, setTransaction] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTransaction(null);

    timeService.findNTPTransaction(transactionId, organizationId, tenantId, selectedTaps, setTransaction)
  }, [transactionId, organizationId, tenantId, selectedTaps])

  if (transaction == null) {
    return <LoadingSpinner />
  }

  let initiated_at = transaction.timestamp_client_tap_receive ?
    transaction.timestamp_client_tap_receive : transaction.timestamp_server_tap_receive;

  return (
    <>
      <div className="row mt-3">
        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Client and Server" />

              <dl className="mb-0">
                <dt>Client Address</dt>
                <dd><L4Address full={true} address={transaction.client} /></dd>
                <dt>Client Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={transaction.client}
                    inner={transaction.client ?
                      <EthernetMacAddress addressWithContext={transaction.client.mac} withAssetLink withAssetName />
                      : null } />
                </dd>
                <dt>Server Address</dt>
                <dd><L4Address full={true} address={transaction.server} /></dd>
                <dt>Server Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={transaction.server}
                    inner={transaction.server ?
                      <EthernetMacAddress addressWithContext={transaction.server.mac} withAssetLink withAssetName />
                      : null } />
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Complete</dt>
                <dd>{transaction.complete ? <span className="text-success">Complete</span>
                  : <span className="text-warning">Incomplete</span>}</dd>
                <dt>Stratum</dt>
                <dd>{transaction.stratum ? transaction.stratum : <span className="text-muted">n/a</span>}</dd>
                <dt>Clock Reference</dt>
                <dd>{transaction.reference_id ? transaction.reference_id : <span className="text-muted">n/a</span>}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Timing &amp; Performance" />

              <dl className="mb-0">
                <dt>Round Trip Time (RTT)</dt>
                <dd>{transaction.rtt_seconds ? <span>{formatSubMicro(transaction.rtt_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</dd>
                <dt>Server Processing Time (SPT)</dt>
                <dd>{transaction.server_processing_seconds ? <span>{formatSubMicro(transaction.server_processing_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</dd>
                <dt>Root Delay</dt>
                <dd>{transaction.root_delay_seconds ? <span>{formatSubMicro(transaction.root_delay_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</dd>
                <dt>Root Dispersion</dt>
                <dd>{transaction.root_dispersion_seconds ? <span>{formatSubMicro(transaction.root_dispersion_seconds)}</span>
                  : <span className="text-muted">n/a</span>}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Initiated at</dt>
                <dd>{moment(initiated_at).format()}</dd>
                <dt>Underlying UDP Session</dt>
                <dd><UDPSessionLink sessionId={transaction.transaction_key} startTime={initiated_at} /></dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

    </>
  )

}