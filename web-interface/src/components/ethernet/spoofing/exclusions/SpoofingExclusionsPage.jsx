import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import SpoofingService from "../../../../services/ethernet/SpoofingService";
import {SPOOFING_MENU_ITEMS} from "../SpoofingMenuItems";
import {SPOOFING_EXCLUSIONS_FILTER_FIELDS} from "./SpoofingExclusionsFilterFields";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import SpoofingExclusionsTable from "./SpoofingExclusionsTable";

const spoofingService = new SpoofingService();


export default function SpoofingExclusionsPage() {

  usePageTitle("Ethernet Spoofing Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(SPOOFING_EXCLUSIONS_FILTER_FIELDS);
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
    spoofingService.findAllExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={SPOOFING_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-spoofing-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={SPOOFING_EXCLUSIONS_FILTER_FIELDS}
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
                Exclusions keep known setups out of spoofing detection. Use them where an address legitimately has
                more than one MAC address at a time, like router redundancy protocols (VRRP, HSRP, CARP), failover
                clusters and virtual machine migration.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <SpoofingExclusionsTable exclusions={exclusions}
                                       page={page}
                                       setPage={setPage}
                                       perPage={perPage}
                                       orderColumn={orderColumn}
                                       setOrderColumn={applyOrderColumn}
                                       orderDirection={orderDirection}
                                       setOrderDirection={applyOrderDirection}
                                       filterFields={SPOOFING_EXCLUSIONS_FILTER_FIELDS}
                                       setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
