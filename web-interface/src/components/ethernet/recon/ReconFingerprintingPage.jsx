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
import ReconFingerprintingTable from "./ReconFingerprintingTable";
import ActiveScansHistogram from "./ActiveScansHistogram";
import {Presets} from "../../shared/timerange/TimeRange";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const reconService = new ReconService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ReconFingerprintingPage() {

  usePageTitle("Ethernet Recon Fingerprinting");

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

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
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

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }

  useEffect(() => {
    setScans(null);
    reconService.findAllScans(
      organizationId, tenantId, selectedTaps, "FINGERPRINTING", filters, timeRange, orderColumn, orderDirection,
      perPage, (page-1)*perPage, setScans
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, selectedTaps, revision]);

  useEffect(() => {
    setHistogram(null);
    reconService.findActiveScansHistogram(organizationId, tenantId, selectedTaps, "FINGERPRINTING", filters, timeRange, setHistogram);
  }, [organizationId, tenantId, selectedTaps, filters, timeRange, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.FINGERPRINTING.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon-fingerprinting" className="btn btn-secondary float-end">Help</a>
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
              <CardTitleWithControls title="Fingerprinting Activity"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveScansHistogram histogram={histogram}
                                    timeRange={timeRange}
                                    setTimeRange={applyTimeRange} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="OS Fingerprinting Attempts"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                {scans ? <span> <strong>Total:</strong> {numeral(scans.total).format("0,0")}</span> : null}
              </p>

              <ReconFingerprintingTable scans={scans}
                                        page={page}
                                        setPage={setPage}
                                        perPage={perPage}
                                        orderColumn={orderColumn}
                                        setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                                        orderDirection={orderDirection}
                                        setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }}
                                        filterFields={RECON_SCANS_FILTER_FIELDS}
                                        setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
