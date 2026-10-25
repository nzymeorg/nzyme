import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import Filters from "../../shared/filtering/Filters";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import ReconService from "../../../services/ethernet/ReconService";
import {RECON_MENU_ITEMS} from "./ReconMenuItems";
import {RECON_SCANS_FILTER_FIELDS} from "./ReconScansFilterFields";
import ReconScansTable from "./ReconScansTable";
import ActiveScansHistogram from "./ActiveScansHistogram";
import {reconScanTypeToName} from "./ReconScanType";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const reconService = new ReconService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ReconScansPage() {

  usePageTitle("Ethernet Recon Scans");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), RECON_SCANS_FILTER_FIELDS)
  );
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [scans, setScans] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const [histogramTimeRange, setHistogramTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [histogram, setHistogram] = useState(null);

  const perPage = 25;

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
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
    setScans(null);
    reconService.findAllScans(
      organizationId, tenantId, selectedTaps, "SCANS", filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setScans
    );
  }, [page, filters, orderColumn, orderDirection, organizationId, tenantId, selectedTaps, revision]);

  useEffect(() => {
    setHistogram(null);
    reconService.findActiveScansHistogram(organizationId, tenantId, selectedTaps, "SCANS", filters, histogramTimeRange, setHistogram);
  }, [organizationId, tenantId, selectedTaps, filters, histogramTimeRange, revision]);

  const typeCounts = () => {
    if (!scans) {
      return null;
    }

    return (
      <p className="mb-3">
        <strong>Total:</strong> {numeral(scans.total).format("0,0")}
        {["TCP_PORT_SCAN", "TCP_HOST_SWEEP", "UDP_PORT_SCAN", "UDP_HOST_SWEEP", "ARP_SWEEP", "ICMP_SWEEP", "DNS_REVERSE_SWEEP", "SLOW_SCAN"].map((t) => {
          return (
            <span key={t} className="ms-3">
              {reconScanTypeToName(t)}: {numeral(scans.type_counts[t] || 0).format("0,0")}
            </span>
          )
        })}
      </p>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.SCANS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={histogramTimeRange}
                                     setTimeRange={setHistogramTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={RECON_SCANS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active Scans"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveScansHistogram histogram={histogram}
                                    timeRange={histogramTimeRange}
                                    setTimeRange={setHistogramTimeRange} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Scans"
                                     refreshAction={() => setRevision(new Date())} />

              {typeCounts()}

              <ReconScansTable scans={scans}
                               page={page}
                               setPage={setPage}
                               perPage={perPage}
                               orderColumn={orderColumn}
                               setOrderColumn={applyOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={applyOrderDirection}
                               filterFields={RECON_SCANS_FILTER_FIELDS}
                               setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
