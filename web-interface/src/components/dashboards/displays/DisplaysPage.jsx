import React, {useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../shared/SectionMenuBar";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {DASHBOARDS_MENU_ITEMS} from "../DashboardsMenuItems";
import DisplaysTable from "./DisplaysTable";

export default function DisplaysPage() {

  usePageTitle("Displays");

  const [organizationId, tenantId] = useSelectedTenant();

  const [revision, setRevision] = useState(new Date());

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={DASHBOARDS_MENU_ITEMS} activeRoute={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Displays"
                                     helpLink="https://go.nzyme.org/dashboards-displays"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">A display is a screen that shows dashboards without a user session.</p>

              <p>
                <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.CREATE} className="btn btn-sm btn-primary">
                  Create Display
                </Link>
              </p>

              <DisplaysTable organizationId={organizationId} tenantId={tenantId} revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
