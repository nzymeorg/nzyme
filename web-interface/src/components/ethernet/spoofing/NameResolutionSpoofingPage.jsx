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
import {NAME_RESOLUTION_SPOOFING_FILTER_FIELDS} from "./NameResolutionSpoofingFilterFields";
import NameResolutionSpoofingTable from "./NameResolutionSpoofingTable";
import NameResolutionRespondersTable from "./NameResolutionRespondersTable";
import ActiveSpoofingFindingsHistogram from "./ActiveSpoofingFindingsHistogram";
import {Presets} from "../../shared/timerange/TimeRange";

const spoofingService = new SpoofingService();


export default function NameResolutionSpoofingPage() {

  usePageTitle("Ethernet Name Resolution Poisoning");

  const [organizationId, tenantId] = useSelectedTenant();

  const [filters, setFilters] = useUrlFilters(NAME_RESOLUTION_SPOOFING_FILTER_FIELDS);

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [findings, setFindings] = useState(null);
  const [histogram, setHistogram] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const [responders, setResponders] = useState(null);
  const [respondersPage, setRespondersPage] = useState(1);
  const [respondersRevision, setRespondersRevision] = useState(new Date());

  const perPage = 25;

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setRespondersPage(1);
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
    spoofingService.findAllNameResolutionFindings(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setFindings
    );
  }, [page, filters, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  useEffect(() => {
    setHistogram(null);
    spoofingService.findActiveNameResolutionFindingsHistogram(organizationId, tenantId, filters, timeRange, setHistogram);
  }, [organizationId, tenantId, filters, timeRange, revision]);

  useEffect(() => {
    setResponders(null);
    spoofingService.findNameResolutionResponders(
      organizationId, tenantId, timeRange, perPage, (respondersPage-1)*perPage, setResponders
    );
  }, [respondersPage, timeRange, organizationId, tenantId, respondersRevision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-nameres" className="btn btn-secondary float-end">Help</a>
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
                       fields={NAME_RESOLUTION_SPOOFING_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active Name Resolution Poisoning"
                                     refreshAction={() => setRevision(new Date())} />

              <ActiveSpoofingFindingsHistogram histogram={histogram}
                                               timeRange={timeRange}
                                               setTimeRange={applyTimeRange}
                                               emptyMessage="No name resolution poisoning in the selected time range." />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Name Resolution Poisoning"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Devices that answered LLMNR, NBT-NS or mDNS queries for more different names than a device
                legitimately has, pointing the names at their own address. Clients that received such an answer
                connect to the responder believing it is the host they looked for, and Windows clients send it
                their credentials. The list covers findings that were active at any point in the selected time
                range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <NameResolutionSpoofingTable findings={findings}
                                           page={page}
                                           setPage={setPage}
                                           perPage={perPage}
                                           orderColumn={orderColumn}
                                           setOrderColumn={applyOrderColumn}
                                           orderDirection={orderDirection}
                                           setOrderDirection={applyOrderDirection}
                                           filterFields={NAME_RESOLUTION_SPOOFING_FILTER_FIELDS}
                                           setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Responders"
                                     refreshAction={() => setRespondersRevision(new Date())} />

              <p className="text-muted">
                Every device that answered name queries on the taps of this tenant in the selected time range, most
                suspicious first. A legitimate device answers for its own name only, so its self-pointing names are
                one or two. The time range cannot reach further back than the configured response retention time.
              </p>

              {responders ? <p className="mb-3"><strong>Total:</strong> {numeral(responders.total).format("0,0")}</p> : null}

              <NameResolutionRespondersTable responders={responders}
                                             page={respondersPage}
                                             setPage={setRespondersPage}
                                             perPage={perPage} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
