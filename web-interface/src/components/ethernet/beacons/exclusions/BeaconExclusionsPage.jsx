import React, {useContext, useEffect, useState} from "react";
import {Link, useLocation} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import BeaconsService from "../../../../services/ethernet/BeaconsService";
import {BEACONS_MENU_ITEMS} from "../BeaconsMenuItems";
import {BEACON_EXCLUSIONS_FILTER_FIELDS} from "./BeaconExclusionsFilterFields";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import BeaconExclusionsTable from "./BeaconExclusionsTable";

const beaconsService = new BeaconsService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function BeaconExclusionsPage() {

  usePageTitle("Ethernet Beacon Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), BEACON_EXCLUSIONS_FILTER_FIELDS)
  );
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const [exclusions, setExclusions] = useState(null);
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
    setExclusions(null);
    beaconsService.findAllExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={BEACONS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-beacons-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={BEACON_EXCLUSIONS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Exclusions"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Exclusions keep traffic out of beacon detection. Use them for destinations you never want to see as
                beacons, like your own update servers, monitoring systems or an entire network.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <BeaconExclusionsTable exclusions={exclusions}
                                     page={page}
                                     setPage={setPage}
                                     perPage={perPage}
                                     orderColumn={orderColumn}
                                     setOrderColumn={applyOrderColumn}
                                     orderDirection={orderDirection}
                                     setOrderDirection={applyOrderDirection}
                                     filterFields={BEACON_EXCLUSIONS_FILTER_FIELDS}
                                     setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
