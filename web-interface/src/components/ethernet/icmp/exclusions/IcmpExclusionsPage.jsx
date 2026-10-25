import React, {useContext, useEffect, useState} from "react";
import {Link, useLocation} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import IcmpService from "../../../../services/ethernet/IcmpService";
import {ICMP_MENU_ITEMS} from "../IcmpMenuItems";
import {ICMP_EXCLUSIONS_FILTER_FIELDS} from "./IcmpExclusionsFilterFields";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import IcmpExclusionsTable from "./IcmpExclusionsTable";

const icmpService = new IcmpService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function IcmpExclusionsPage() {

  usePageTitle("ICMP Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");
  const urlQuery = useQuery();

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), ICMP_EXCLUSIONS_FILTER_FIELDS)
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

  useEffect(() => {
    setExclusions(null);
    icmpService.findAllExclusions(organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions);
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ICMP_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-icmp-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ICMP_EXCLUSIONS_FILTER_FIELDS}
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
                Exclusions keep known good sources out of redirect, tunnel and traceroute detection. Use them for a
                legitimate second router that sends redirects, or a monitoring host that runs traceroutes.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <IcmpExclusionsTable exclusions={exclusions}
                                   page={page}
                                   setPage={setPage}
                                   perPage={perPage}
                                   orderColumn={orderColumn}
                                   setOrderColumn={(c) => { setPage(1); setOrderColumn(c); }}
                                   orderDirection={orderDirection}
                                   setOrderDirection={(d) => { setPage(1); setOrderDirection(d); }}
                                   setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
