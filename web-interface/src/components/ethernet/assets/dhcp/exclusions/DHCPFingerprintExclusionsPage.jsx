import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import Filters from "../../../../shared/filtering/Filters";
import useUrlFilters from "../../../../shared/filtering/useUrlFilters";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import {ASSETS_MENU_ITEMS} from "../../AssetsMenuItems";
import {DHCP_FINGERPRINT_EXCLUSIONS_FILTER_FIELDS} from "./DHCPFingerprintExclusionsFilterFields";
import {UserContext} from "../../../../../App";
import {userHasPermission} from "../../../../../util/Tools";
import DHCPFingerprintExclusionsTable from "./DHCPFingerprintExclusionsTable";

const assetsService = new AssetsService();

export default function DHCPFingerprintExclusionsPage() {

  usePageTitle("DHCP Fingerprint Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [filters, setFilters] = useUrlFilters(DHCP_FINGERPRINT_EXCLUSIONS_FILTER_FIELDS);
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
    assetsService.findAllDHCPFingerprintExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-dhcp-fingerprint-exclusions" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={DHCP_FINGERPRINT_EXCLUSIONS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="DHCP Fingerprint Exclusions"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Exclusions keep assets with known flaky DHCP fingerprints from raising the <em>new DHCP
                fingerprint</em> alert. Nzyme keeps tracking the fingerprints of excluded assets, it only stops
                alerting on changes. Use them for devices that legitimately alternate between DHCP clients, like
                dual-boot machines, or for a vendor prefix whose devices randomize DHCP options. The table of
                assets with flaky fingerprints on the <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX}>DHCP page</Link>{' '}
                helps to find candidates.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <DHCPFingerprintExclusionsTable exclusions={exclusions}
                                              page={page}
                                              setPage={setPage}
                                              perPage={perPage}
                                              orderColumn={orderColumn}
                                              setOrderColumn={applyOrderColumn}
                                              orderDirection={orderDirection}
                                              setOrderDirection={applyOrderDirection}
                                              filterFields={DHCP_FINGERPRINT_EXCLUSIONS_FILTER_FIELDS}
                                              setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
