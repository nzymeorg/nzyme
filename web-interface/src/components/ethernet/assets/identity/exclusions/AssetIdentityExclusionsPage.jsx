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
import {ASSET_IDENTITY_EXCLUSIONS_FILTER_FIELDS} from "./AssetIdentityExclusionsFilterFields";
import {UserContext} from "../../../../../App";
import {userHasPermission} from "../../../../../util/Tools";
import AssetIdentityExclusionsTable from "./AssetIdentityExclusionsTable";

const assetsService = new AssetsService();

export default function AssetIdentityExclusionsPage() {

  usePageTitle("Asset Identity Exclusions");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [filters, setFilters] = useUrlFilters(ASSET_IDENTITY_EXCLUSIONS_FILTER_FIELDS);
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
    assetsService.findAllAssetIdentityExclusions(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, setExclusions
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/ethernet-assets-identity-exclusions" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ASSET_IDENTITY_EXCLUSIONS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Asset Identity Exclusions"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Exclusions keep assets from raising identity findings. Nzyme keeps tracking the identity attributes
                of excluded assets, it only stops reporting findings for them.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.CREATE} className="btn btn-sm btn-primary">
                    Create Exclusion
                  </Link>
                </p> : null}

              <AssetIdentityExclusionsTable exclusions={exclusions}
                                              page={page}
                                              setPage={setPage}
                                              perPage={perPage}
                                              orderColumn={orderColumn}
                                              setOrderColumn={applyOrderColumn}
                                              orderDirection={orderDirection}
                                              setOrderDirection={applyOrderDirection}
                                              filterFields={ASSET_IDENTITY_EXCLUSIONS_FILTER_FIELDS}
                                              setFilters={applyFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
