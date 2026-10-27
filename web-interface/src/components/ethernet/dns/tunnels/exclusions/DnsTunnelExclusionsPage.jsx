import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import Filters from "../../../../shared/filtering/Filters";
import useUrlFilters from "../../../../shared/filtering/useUrlFilters";
import DnsTunnelsService from "../../../../../services/ethernet/DnsTunnelsService";
import {DNS_MENU_ITEMS} from "../../DNSMenuItems";
import {DNS_TUNNEL_EXCLUSIONS_FILTER_FIELDS} from "./DnsTunnelExclusionsFilterFields";
import {UserContext} from "../../../../../App";
import {userHasPermission} from "../../../../../util/Tools";
import DnsTunnelExclusionsTable from "./DnsTunnelExclusionsTable";

const dnsTunnelsService = new DnsTunnelsService();


export default function DnsTunnelExclusionsPage() {

  usePageTitle("Ethernet DNS Tunnel Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(DNS_TUNNEL_EXCLUSIONS_FILTER_FIELDS);
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
    dnsTunnelsService.findAllExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={DNS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-dns-tunnels-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={DNS_TUNNEL_EXCLUSIONS_FILTER_FIELDS}
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
                Exclusions keep known services out of DNS tunnel detection. Use them for services that encode data
                in DNS names on purpose, like antivirus and reputation lookups, DNS blocklists and content delivery
                networks with random hostnames, or for clients that are expected to look up such names.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <DnsTunnelExclusionsTable exclusions={exclusions}
                                        page={page}
                                        setPage={setPage}
                                        perPage={perPage}
                                        orderColumn={orderColumn}
                                        setOrderColumn={applyOrderColumn}
                                        orderDirection={orderDirection}
                                        setOrderDirection={applyOrderDirection}
                                        filterFields={DNS_TUNNEL_EXCLUSIONS_FILTER_FIELDS}
                                        setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
