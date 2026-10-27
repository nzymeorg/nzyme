import React, {useEffect, useState} from "react";
import numeral from "numeral";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import Filters from "../../../shared/filtering/Filters";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import DnsTunnelsService from "../../../../services/ethernet/DnsTunnelsService";
import {DNS_MENU_ITEMS} from "../DNSMenuItems";
import {DNS_TUNNEL_FILTER_FIELDS} from "./DnsTunnelFilterFields";
import DnsTunnelsTable from "./DnsTunnelsTable";
import ActiveSpoofingFindingsHistogram from "../../spoofing/ActiveSpoofingFindingsHistogram";
import {Presets} from "../../../shared/timerange/TimeRange";

const dnsTunnelsService = new DnsTunnelsService();


export default function DnsTunnelsPage() {

  usePageTitle("Ethernet DNS Tunnels");

  const [organizationId, tenantId] = useSelectedTenant();

  const [filters, setFilters] = useUrlFilters(DNS_TUNNEL_FILTER_FIELDS);

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
    dnsTunnelsService.findAllFindings(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setFindings
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  useEffect(() => {
    setHistogram(null);
    dnsTunnelsService.findActiveFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, timeRange, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={DNS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-dns-tunnels" className="btn btn-secondary float-end">Help</a>
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
                       fields={DNS_TUNNEL_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active DNS Tunnels"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveSpoofingFindingsHistogram histogram={histogram}
                                               timeRange={timeRange}
                                               setTimeRange={applyTimeRange}
                                               emptyMessage="No DNS tunnels in the selected time range." />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DNS Tunnels"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Clients that looked up many distinct names under one registered domain, where the names look like
                encoded data: high entropy, unusual length or unusual record types. A DNS tunnel moves data in the
                names it asks for and in the replies it gets, and passes through any firewall that allows DNS.
                The list covers findings that were active at any point in the selected time range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <DnsTunnelsTable findings={findings}
                               page={page}
                               setPage={setPage}
                               perPage={perPage}
                               orderColumn={orderColumn}
                               setOrderColumn={applyOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={applyOrderDirection}
                               filterFields={DNS_TUNNEL_FILTER_FIELDS}
                               setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
