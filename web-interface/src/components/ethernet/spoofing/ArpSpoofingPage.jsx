import React, {useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import Filters from "../../shared/filtering/Filters";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import {SPOOFING_MENU_ITEMS} from "./SpoofingMenuItems";
import {ARP_SPOOFING_FILTER_FIELDS} from "./ArpSpoofingFilterFields";
import ArpSpoofingTable from "./ArpSpoofingTable";
import ActiveArpSpoofingHistogram from "./ActiveArpSpoofingHistogram";
import {Presets} from "../../shared/timerange/TimeRange";

const spoofingService = new SpoofingService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ArpSpoofingPage() {

  usePageTitle("Ethernet ARP Poisoning");

  const [organizationId, tenantId] = useSelectedTenant();
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), ARP_SPOOFING_FILTER_FIELDS)
  );

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
    spoofingService.findAllArpFindings(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setFindings
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  useEffect(() => {
    setHistogram(null);
    spoofingService.findActiveArpFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, timeRange, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-arp" className="btn btn-secondary float-end">Help</a>
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
                       fields={ARP_SPOOFING_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active ARP Poisoning"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveArpSpoofingHistogram histogram={histogram}
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
              <CardTitleWithControls title="ARP Poisoning"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Addresses that a device claimed with its own MAC address while the legitimate owner was still
                claiming them as well. The spoofer is the device that showed up second. Victims are the devices
                that received the spoofer&apos;s replies and may now send their traffic for the address to the
                spoofer. The list covers findings that were active at any point in the selected time range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <ArpSpoofingTable findings={findings}
                                page={page}
                                setPage={setPage}
                                perPage={perPage}
                                orderColumn={orderColumn}
                                setOrderColumn={applyOrderColumn}
                                orderDirection={orderDirection}
                                setOrderDirection={applyOrderDirection}
                                filterFields={ARP_SPOOFING_FILTER_FIELDS}
                                setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
