import React, {useEffect, useState} from 'react';
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {MONITORING_MENU_ITEMS} from "../Dot11MonitoringMenuItems";
import Dot11Service from "../../../../services/Dot11Service";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ProbeRequestsTable from "./ProbeRequestsTable";
import {toast} from "react-toastify";
import usePageTitle from "../../../../util/UsePageTitle";

const dot11Service = new Dot11Service();

export default function ProbeRequestsPage() {

  usePageTitle("Probe Request Monitoring");

  const [organizationId, tenantId] = useSelectedTenant();
  const [probeRequests, setProbeRequests] = useState(null);

  const [revision, setRevision] = useState(new Date());

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setProbeRequests(null);
    dot11Service.findAllMonitoredProbeRequests(
      organizationId, tenantId, perPage, (page-1)*perPage, setProbeRequests
    )
  }, [organizationId, tenantId, page, revision]);

  const onDelete = (e, id) => {
    e.preventDefault();

    if (!confirm("Really delete monitored probe request?")) {
      return;
    }

    dot11Service.deleteMonitoredProbeRequest(id, () => {
      toast.success('Monitored probe request deleted.');
      setRevision(new Date());
    });
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-10">
            <SectionMenuBar items={MONITORING_MENU_ITEMS}
                            activeRoute={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX}/>
          </div>

          <div className="col-md-2">
            <a href="https://go.nzyme.org/wifi-probereq-monitoring" className="btn btn-secondary float-end">Help</a>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Probe Request Monitoring</h3>

                <p className="text-muted mb-0">
                  Monitoring probe requests is essential for ensuring that sensitive SSIDs are not broadcasted by
                  your devices, especially in secure or sensitive locations. For instance, if you provide WiFi
                  access in a high-security area, it is crucial to prevent users from inadvertently revealing
                  that they have previously connected to this network elsewhere. By using nzyme to monitor probe
                  requests, you can enforce a policy that requires users to delete certain networks from their
                  devices or disable the &quot;auto-connect&quot; feature.
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Monitored Probe Requests</h3>

                <ProbeRequestsTable probeRequests={probeRequests}
                                    onDelete={onDelete}
                                    page={page}
                                    setPage={setPage}
                                    perPage={perPage} />


                <a href={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.CREATE}
                   className="btn btn-sm btn-secondary">
                  Create Monitored Probe Request
                </a>

              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
)

}