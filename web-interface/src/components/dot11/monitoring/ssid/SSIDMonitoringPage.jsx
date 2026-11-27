import React, {useEffect, useState} from 'react';
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {MONITORING_MENU_ITEMS} from "../Dot11MonitoringMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import Dot11Service from "../../../../services/Dot11Service";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {toast} from "react-toastify";
import SSIDMonitoringConfiguration from "./SSIDMonitoringConfiguration";
import KnownNetworksTable from "./KnownNetworksTable";
import usePageTitle from "../../../../util/UsePageTitle";
import ApproveKnownNetworksDialog from "./ApproveKnownNetworksDialog";
import HiddenAccessPoints from "./HiddenAccessPoints";
import {isBehindLastPage} from "../../../ethernet/segmentation/SegmentationBulkActions";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";

const dot11Service = new Dot11Service();

export default function SSIDMonitoringPage() {

  usePageTitle("SSID Monitoring");

  const [organizationId, tenantId] = useSelectedTenant();

  const [networks, setNetworks] = useState(null);
  const [configuration, setConfiguration] = useState(null);

  const [revision, setRevision] = useState(new Date());

  const [perPage, setPerPage] = useState(25);
  const [page, setPage] = useState(1);

  const [orderColumn, setOrderColumn] = useState("ssid");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const [approvalDialogShown, setApprovalDialogShown] = useState(false);

  useEffect(() => {
    setNetworks(null);
    setConfiguration(null);
  }, [organizationId, tenantId]);

  // Not reset while loading again: a spinner in place of the table changes the page height and moves the scroll position.
  useEffect(() => {
    const controller = new AbortController();

    dot11Service.findAllKnownNetworks(
      organizationId, tenantId, orderColumn, orderDirection, perPage, (page-1)*perPage, (response) => {
        if (isBehindLastPage(response.networks, response.total, page)) {
          setPage(Math.ceil(response.total / perPage));
        } else {
          setNetworks(response);
        }
      }, controller.signal
    );

    return () => controller.abort();
  }, [page, perPage, orderColumn, orderDirection, organizationId, tenantId, revision])

  const onDeleteAll = (e) => {
    e.preventDefault();

    if (!confirm("Really delete all known networks? Each will reappear as unapproved network " +
      "next time Nzyme records it.")) {
      return;
    }

    dot11Service.deleteAllKnownNetworks(organizationId, tenantId, () => {
      toast.success('All known networks deleted.');
      onChange();
    });
  }

  const onApproveAll = (e) => {
    e.preventDefault();

    if (!confirm("Really approve all known networks?")) {
      return;
    }

    dot11Service.approveAllKnownNetworks(organizationId, tenantId, () => {
      toast.success('All known networks approved.');
      onChange();
    });
  }

  const onChange = () => {
    setRevision(new Date());
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={MONITORING_MENU_ITEMS}
                            activeRoute={ApiRoutes.DOT11.MONITORING.SSIDS.INDEX}/>
          </div>
        </div>

        <React.Fragment>
          <div className="row mt-3">
            <div className="col-xl-12 col-xxl-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Monitor Configuration"
                                         helpLink="https://go.nzyme.org/wifi-ssid-monitoring-configuration" />

                  <SSIDMonitoringConfiguration organizationUUID={organizationId}
                                               tenantUUID={tenantId}
                                               onLoaded={setConfiguration} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Known SSIDs/Networks"
                                         helpLink="https://go.nzyme.org/wifi-ssid-monitoring-networks" />

                  <div className="row align-items-center">
                    <div className="col-auto">
                      <label className="col-form-label">Per Page:</label>
                    </div>
                    <div className="col-auto">
                      <select className="form-select"
                              onChange={(e) => {e.preventDefault(); setPage(1); setPerPage(Number(e.target.value))}}>
                        <option value={25}>25</option>
                        <option value={50}>50</option>
                        <option value={100}>100</option>
                        <option value={250}>250</option>
                      </select>
                    </div>
                    <div className="col text-end">
                      <button className="btn btn-secondary btn-sm me-1"
                              onClick={() => setApprovalDialogShown(true)}>
                        Approve by Pattern
                      </button>
                      <button className="btn btn-secondary btn-sm me-1" onClick={onApproveAll}>Approve All</button>
                      <button className="btn btn-danger btn-sm" onClick={onDeleteAll}>Delete All</button>
                    </div>
                  </div>

                  <KnownNetworksTable networks={networks}
                                      onChange={onChange}
                                      page={page}
                                      setPage={setPage}
                                      perPage={perPage}
                                      orderColumn={orderColumn}
                                      setOrderColumn={setOrderColumn}
                                      orderDirection={orderDirection}
                                      setOrderDirection={setOrderDirection} />

                  {approvalDialogShown ?
                    <ApproveKnownNetworksDialog onClose={() => setApprovalDialogShown(false)}
                                                onComplete={() => setRevision(new Date())} />
                    : null }
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-12">
              <HiddenAccessPoints configuration={configuration} cardsAboveLoaded={networks !== null} />
            </div>
          </div>
        </React.Fragment>
      </React.Fragment>
  )

}