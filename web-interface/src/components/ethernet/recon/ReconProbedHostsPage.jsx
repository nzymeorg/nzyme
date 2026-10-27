import React, {useEffect, useState} from "react";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import ReconService from "../../../services/ethernet/ReconService";
import {RECON_MENU_ITEMS} from "./ReconMenuItems";
import {RECON_PROBED_HOSTS_DEFAULT_FILTERS, RECON_PROBED_HOSTS_FILTER_FIELDS} from "./ReconProbedHostsFilterFields";
import ReconProbedHostsTable from "./ReconProbedHostsTable";
import {Presets} from "../../shared/timerange/TimeRange";

const reconService = new ReconService();

export default function ReconProbedHostsPage() {

  usePageTitle("Ethernet Recon Probed Hosts");

  const [organizationId, tenantId] = useSelectedTenant();
  // The default filters apply unless the URL carries its own set, which is also how a cleared filter set is kept.
  const [filters, setFilters] = useUrlFilters(
    RECON_PROBED_HOSTS_FILTER_FIELDS,
    queryParametersToFilters(JSON.stringify(RECON_PROBED_HOSTS_DEFAULT_FILTERS), RECON_PROBED_HOSTS_FILTER_FIELDS)
  );
  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [includeScanners, setIncludeScanners] = useState(false);
  const [orderColumn, setOrderColumn] = useState("failed_port_count");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [hosts, setHosts] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }

  const applyIncludeScanners = (value) => {
    setPage(1);
    setIncludeScanners(value);
  }

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  useEffect(() => {
    setHosts(null);
    reconService.findProbedHosts(
      organizationId, tenantId, timeRange, filters, includeScanners, orderColumn, orderDirection, perPage,
      (page-1)*perPage, setHosts
    );
  }, [page, timeRange, filters, includeScanners, orderColumn, orderDirection, organizationId, tenantId, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.HOSTS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon-hosts" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={RECON_PROBED_HOSTS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />

              <div className="form-check mt-2">
                <input className="form-check-input" type="checkbox" id="probed-hosts-include-scanners"
                       checked={includeScanners}
                       onChange={(e) => applyIncludeScanners(e.target.checked)} />
                <label className="form-check-label" htmlFor="probed-hosts-include-scanners">
                  Include probes from devices already detected as scanners
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Probed Hosts"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                This is a view of connection attempts from the receiving side. Every host that any device tried to
                reach is in it, so most rows are ordinary traffic. Any port that was tried but never answered counts as
                unanswered, including a service that was briefly down or a host that left the network. The
                rows worth a look are the ones probed by many devices with many unanswered ports: that is the shape
                of a scan spread across devices that no single scanner reveals. Probes from devices that are already
                listed as scans are left out unless you include them above. The time range cannot reach further
                back than the slow scan memory.
              </p>

              {hosts ? <p className="mb-3"><strong>Total:</strong> {numeral(hosts.total).format("0,0")}</p> : null}

              <ReconProbedHostsTable hosts={hosts}
                                     page={page}
                                     setPage={setPage}
                                     perPage={perPage}
                                     orderColumn={orderColumn}
                                     setOrderColumn={applyOrderColumn}
                                     orderDirection={orderDirection}
                                     setOrderDirection={applyOrderDirection}
                                     filterFields={RECON_PROBED_HOSTS_FILTER_FIELDS}
                                     setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
