import React, {useContext, useEffect, useState} from "react";
import {Link, useLocation} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import ReconService from "../../../../services/ethernet/ReconService";
import {RECON_MENU_ITEMS} from "../ReconMenuItems";
import {RECON_EXCLUSIONS_FILTER_FIELDS} from "./ReconExclusionsFilterFields";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import ReconExclusionsTable from "./ReconExclusionsTable";

const reconService = new ReconService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ReconExclusionsPage() {

  usePageTitle("Ethernet Recon Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), RECON_EXCLUSIONS_FILTER_FIELDS)
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
    reconService.findAllExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={RECON_EXCLUSIONS_FILTER_FIELDS}
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
                Exclusions keep devices out of scan detection. Use them for devices that probe your network on
                purpose, like vulnerability scanners, monitoring systems and asset discovery tools.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <ReconExclusionsTable exclusions={exclusions}
                                    page={page}
                                    setPage={setPage}
                                    perPage={perPage}
                                    orderColumn={orderColumn}
                                    setOrderColumn={applyOrderColumn}
                                    orderDirection={orderDirection}
                                    setOrderDirection={applyOrderDirection}
                                    filterFields={RECON_EXCLUSIONS_FILTER_FIELDS}
                                    setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
