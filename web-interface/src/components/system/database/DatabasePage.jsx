import React, {useEffect, useState} from "react";
import SystemService from "../../../services/SystemService";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import GlobalDatabaseUsageTable from "./GlobalDatabaseUsageTable";
import SubsystemDatabaseUsageTable from "./SubsystemDatabaseUsageTable";
import {toast} from "react-toastify";
import usePageTitle from "../../../util/UsePageTitle";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {DATABASE_MENU_ITEMS} from "./DatabaseMenuItems";

const systemService = new SystemService();

function DatabasePage() {

  usePageTitle("Database");

  const [sizes, setSizes] = useState();
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setSizes(null);
    systemService.getDatabaseGlobalSizes(setSizes);
  }, [revision])

  const onPurge = () => {
    setRevision(new Date());
    toast.success('Data purge request submitted. It can take a while to complete.');
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-12">
            <SectionMenuBar items={DATABASE_MENU_ITEMS} activeRoute={ApiRoutes.SYSTEM.DATABASE.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <h1>Database</h1>
          </div>
        </div>

        <div className="row">
          <div className="col-12">
            <div className="row mt-3">
              <div className="col-xl-12 col-xxl-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Global Database Usage per Subsystem" slim={true} />

                    <SubsystemDatabaseUsageTable sizes={sizes ? sizes.subsystem_sizes : null} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-xl-12 col-xxl-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Global Database Usage"
                                           slim={true}
                                           helpLink="https://go.nzyme.org/retention-time" />

                    <GlobalDatabaseUsageTable sizes={sizes} onPurge={onPurge} onRefresh={() => setRevision(new Date())} />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default DatabasePage;