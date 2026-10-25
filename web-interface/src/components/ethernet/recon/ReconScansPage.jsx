import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
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
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
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

  // The type counts arrive with the scans list and are shaped here like a histogram response.
  const typeHistogram = () => {
    if (!scans) {
      return null;
    }

    const values = Object.keys(scans.type_counts)
      .filter((t) => scans.type_counts[t] > 0)
      .sort((a, b) => scans.type_counts[b] - scans.type_counts[a])
      .map((t) => {
        return {
          column_one: {type: "GENERIC", value: reconScanTypeToName(t), metadata: {type: t}},
          column_two: {type: "INTEGER", value: scans.type_counts[t]}
        }
      });

    return {total: scans.total, show_bar_chart: true, values: values};
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
              <CardTitleWithControls title="Scans by Type"
                                     refreshAction={() => setRevision(new Date())} />

              {scans && scans.total === 0 ?
                <div className="alert alert-info mb-0">No scans match the current filters.</div>
                : <TwoColumnHistogram data={typeHistogram()}
                                      columnTitles={["Type", "Scans"]}
                                      columnFilterElements={[
                                        {field: "type", metadataField: "type", fields: RECON_SCANS_FILTER_FIELDS, setFilters: applyFilters},
                                        null
                                      ]}
                                      customChartMarginLeft={140} />}
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
