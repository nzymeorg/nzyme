import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Link, useLocation, useParams} from "react-router-dom";
import AssetsService from "../../../../services/ethernet/AssetsService";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import DHCPTransactionSuccess from "./DHCPTransactionSuccess";
import DHCPDuration from "./DHCPDuration";
import DHCPNotes from "./DHCPNotes";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import DHCPOfferedIpAddresses from "./DHCPOfferedIpAddresses";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import L4Address from "../../shared/L4Address";
import usePageTitle from "../../../../util/UsePageTitle";
import DHCPServerResponses from "./DHCPServerResponses";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

const assetsService = new AssetsService();

export default function DHCPTransactionDetailsPage() {

  const [organizationId, tenantId] = useSelectedTenant();

  let urlQuery = useQuery();
  const tapContext = useContext(TapContext);

  const { transactionId } = useParams();
  const selectedTaps = tapContext.taps;

  const transactionTime = urlQuery.get("transaction_time") ? urlQuery.get("transaction_time") : null;

  const [tx, setTx] = useState(null);

  usePageTitle(tx ? `DHCP TX: ${tx.transaction_id}` : "DHCP Transaction Details");

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTx(null);
    assetsService.findDHCPTransaction(organizationId, tenantId, transactionId, transactionTime, selectedTaps, setTx)
  }, [transactionId, transactionTime, organizationId, tenantId])

  if (!transactionTime) {
    return <div className="alert alert-danger">No Transaction Time passed.</div>
  }

  if (!tx) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <h1>DHCP Transaction <span className="machine-data">{transactionId}</span> <small>at {moment(transactionTime).format()}</small></h1>
          </div>

          <div className="col-md-4 text-end">
            <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX} className="btn btn-primary">Back to Transactions</Link>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Transaction" />

                <dl>
                  <dt>ID</dt>
                  <dd>{tx.transaction_id}</dd>
                  <dt>Type</dt>
                  <dd>{tx.transaction_type}</dd>
                  <dt>Complete</dt>
                  <dd>{tx.is_complete ? <span className="text-success">Complete</span>
                      : <span className="text-warning">Incomplete</span>}</dd>
                  <dt>Successful</dt>
                  <dd><DHCPTransactionSuccess success={tx.is_successful} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Devices" />

                <dl>
                  <dt>Client</dt>
                  <dd>
                    <EthernetMacAddress addressWithContext={tx.client_mac} withAssetLink withAssetName />
                  </dd>
                  <dt>Server</dt>
                  <dd>
                    {tx.server_mac ? <EthernetMacAddress addressWithContext={tx.server_mac} withAssetLink withAssetName />
                        : <span className="text-muted">n/a</span>}
                  </dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Timing" />

                <dl>
                  <dt>First Packet</dt>
                  <dd>
                    {moment(tx.first_packet).format("YYYY-MM-DDTHH:mm:ss.SSSZ")} ({moment(tx.first_packet).fromNow()})
                  </dd>
                  <dt>Latest Packet</dt>
                  <dd>
                    {moment(tx.latest_packet).format("YYYY-MM-DDTHH:mm:ss.SSSZ")} ({moment(tx.latest_packet).fromNow()})
                  </dd>
                  <dt>Duration</dt>
                  <dd><DHCPDuration duration={tx.duration_ms} /></dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Notes" />

                <DHCPNotes tx={tx} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Fingerprint" />

                <dl className="mb-0">
                  <dt>Transaction Fingerprint</dt>
                  <dd><span className="machine-data">{tx.fingerprint}</span></dd>
                  <dt>DHCP Options</dt>
                  <dd>{tx.options != null && tx.options.length > 0 ?
                      <span className="machine-data">{tx.options.join(",")}</span>
                      : <span className="text-muted">None</span>}</dd>
                  <dt>Vendor Class</dt>
                  <dd>{tx.vendor_class ? <span className="machine-data">{tx.vendor_class}</span>
                      : <span className="text-muted">None</span>}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Server Responses" />

                <p className="text-muted">
                  Every server that answered this transaction and the configuration it handed out. More than one
                  server answering is normal with redundant DHCP servers, but a server you do not recognize may be
                  a rogue server. The spoofing detection on the <em>Spoofing</em> page keeps track of this.
                </p>

                <DHCPServerResponses responses={tx.server_responses} selectedServerAddress={tx.selected_server_address} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Transaction Steps" />

                <dl>
                  <dt>Offered IP Addresses</dt>
                  <dd><DHCPOfferedIpAddresses ips={tx.offered_ip_addresses} /></dd>
                  <dt>Requested IP Address</dt>
                  <dd>
                    {tx.requested_ip_address ? tx.requested_ip_address.address : <span className="text-muted">n/a</span>}
                  </dd>
                </dl>

                <table className="table table-sm table-hover table-striped">
                  <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Step</th>
                  </tr>
                  </thead>
                  <tbody>
                  {tx.timeline.map((t, i) => {
                    return (
                        <tr key={i}>
                          <td>{moment(t.timestamp).format("YYYY-MM-DDTHH:mm:ss.SSSZ")}</td>
                          <td>{t.step}</td>
                        </tr>
                    )
                  })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}