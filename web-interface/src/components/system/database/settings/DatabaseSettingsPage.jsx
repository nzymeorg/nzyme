import React from "react";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {DATABASE_MENU_ITEMS} from "../DatabaseMenuItems";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import MaintenanceSettings from "./MaintenanceSettings";
import MaintenanceWindowSettings from "./MaintenanceWindowSettings";
import TableClassSettings from "./TableClassSettings";

export default function DatabaseSettingsPage() {

  usePageTitle("Database Settings");

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={DATABASE_MENU_ITEMS} activeRoute={ApiRoutes.SYSTEM.DATABASE.SETTINGS} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Database Maintenance Settings" />

              <MaintenanceSettings />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Maintenance Window" />

              <MaintenanceWindowSettings />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h2>Table Classes</h2>
          <p className="text-muted">
            Every Nzyme table belongs to one of three classes with their own autovacuum thresholds and page fill factor. The
            percentages are relative to the row count of each table. Changes are applied to the tables immediately.
          </p>
        </div>
      </div>

      <div className="row">
        <div className="col-12">
          <TableClassSettings />
        </div>
      </div>
    </React.Fragment>
  )

}
