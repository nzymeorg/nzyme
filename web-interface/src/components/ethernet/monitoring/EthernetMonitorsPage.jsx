import React, {useEffect, useState} from "react";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {ETHERNET_MONITORING_MENU_ITEMS} from "./EthernetMonitoringMenuItems";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import MonitorsService from "../../../services/MonitorsService";
import MonitorsTable from "../../monitors/shared/MonitorsTable";
import Filters from "../../shared/filtering/Filters";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import {ETHERNET_MONITORS_FILTER_FIELDS} from "./EthernetMonitorsFilterFields";

const monitorsService = new MonitorsService();


export default function EthernetMonitorsPage() {

  usePageTitle("Ethernet Monitors");

  const [organizationId, tenantId] = useSelectedTenant();

  const [filters, setFilters] = useUrlFilters(ETHERNET_MONITORS_FILTER_FIELDS);
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const [monitors, setMonitors] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

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
    setMonitors(null);
    monitorsService.findAllOfSubsystem(
      "ETHERNET", organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage,
      setMonitors
    )
  }, [page, filters, orderColumn, orderDirection, organizationId, tenantId, revision])

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ETHERNET_MONITORING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.MONITORING.MONITORS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-monitors" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ETHERNET_MONITORS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Monitors"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Monitors of all Ethernet data sources. Create a new monitor by saving a filter on any Ethernet
                search page.
              </p>

              {monitors ? <p><strong>Total:</strong> {numeral(monitors.total).format("0,0")}</p> : null}

              <MonitorsTable monitors={monitors}
                             page={page}
                             setPage={setPage}
                             perPage={perPage}
                             showType={true}
                             orderColumn={orderColumn}
                             setOrderColumn={applyOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={applyOrderDirection}
                             filterFields={ETHERNET_MONITORS_FILTER_FIELDS}
                             setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
