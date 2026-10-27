import React, {useEffect, useState} from "react";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import Filters from "../../shared/filtering/Filters";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import {SPOOFING_MENU_ITEMS} from "./SpoofingMenuItems";
import {DNS_SPOOFING_FILTER_FIELDS} from "./DnsSpoofingFilterFields";
import DnsSpoofingTable from "./DnsSpoofingTable";
import ActiveSpoofingFindingsHistogram from "./ActiveSpoofingFindingsHistogram";
import {Presets} from "../../shared/timerange/TimeRange";

const spoofingService = new SpoofingService();


export default function DnsSpoofingPage() {

  usePageTitle("Ethernet DNS Spoofing");

  const [organizationId, tenantId] = useSelectedTenant();

  const [filters, setFilters] = useUrlFilters(DNS_SPOOFING_FILTER_FIELDS);

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [findings, setFindings] = useState(null);
  const [histogram, setHistogram] = useState(null);
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

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  useEffect(() => {
    setFindings(null);
    spoofingService.findAllDnsFindings(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setFindings
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  useEffect(() => {
    setHistogram(null);
    spoofingService.findActiveDnsFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, timeRange, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-dns" className="btn btn-secondary float-end">Help</a>
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
                       fields={DNS_SPOOFING_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active DNS Spoofing"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveSpoofingFindingsHistogram histogram={histogram}
                                               timeRange={timeRange}
                                               setTimeRange={applyTimeRange}
                                               emptyMessage="No DNS spoofing in the selected time range." />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DNS Spoofing"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Devices that answered DNS queries meant for a resolver with forged replies carrying the resolver&apos;s
                address, while the resolver&apos;s real replies were still arriving from the device that normally
                delivers them. A client takes the first reply it gets, so a forged reply that arrives first sends the
                client to an address of the spoofer&apos;s choosing. The list covers findings that were active at any
                point in the selected time range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <DnsSpoofingTable findings={findings}
                                page={page}
                                setPage={setPage}
                                perPage={perPage}
                                orderColumn={orderColumn}
                                setOrderColumn={applyOrderColumn}
                                orderDirection={orderDirection}
                                setOrderDirection={applyOrderDirection}
                                filterFields={DNS_SPOOFING_FILTER_FIELDS}
                                setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
