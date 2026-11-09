import React, {useEffect, useState} from "react";
import {toast} from "react-toastify";
import SystemService from "../../../../services/SystemService";
import usePageTitle from "../../../../util/UsePageTitle";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {DATABASE_MENU_ITEMS} from "../DatabaseMenuItems";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import DatabaseIndicatorsCard from "./DatabaseIndicatorsCard";
import DatabaseServerCard from "./DatabaseServerCard";
import DatabaseTrendsCard from "./DatabaseTrendsCard";
import DatabaseSettingsTable from "./DatabaseSettingsTable";
import DatabaseTablesTable from "./DatabaseTablesTable";
import DatabaseIndexesTable from "./DatabaseIndexesTable";
import MaintenanceLogTable from "./MaintenanceLogTable";
import MaintenanceActivity from "./MaintenanceActivity";
import DatabaseSummaryStrip from "./DatabaseSummaryStrip";

const systemService = new SystemService();

export default function DatabaseHealthPage() {

  usePageTitle("Database Health & Maintenance");

  const [health, setHealth] = useState(null);
  const [notReady, setNotReady] = useState(false);
  const [indicators, setIndicators] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const load = () => {
      systemService.getDatabaseHealth((h) => { setNotReady(false); setHealth(h); }, () => setNotReady(true));
      systemService.getHealthIndicators(setIndicators);
    };

    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [revision]);

  const refresh = () => setRevision((r) => r + 1);

  const queued = (message) => () => {
    toast.success(message);
    refresh();
  };

  const onVacuum = (table) => systemService.vacuumDatabaseTable(table, queued("Vacuum of \"" + table + "\" queued."));
  const onReindexTable = (table) => systemService.reindexDatabaseTable(table, queued("Rebuild of all indexes of \"" + table + "\" queued."));
  const onReindexIndex = (index) => systemService.reindexDatabaseIndex(index, queued("Rebuild of index \"" + index + "\" queued."));

  const menu = (
    <div className="row">
      <div className="col-12">
        <SectionMenuBar items={DATABASE_MENU_ITEMS} activeRoute={ApiRoutes.SYSTEM.DATABASE.HEALTH} />
      </div>
    </div>
  );

  if (notReady) {
    return (
      <React.Fragment>
        {menu}
        <div className="row mt-3">
          <div className="col-12">
            <h1>Database Health &amp; Maintenance</h1>
            <div className="alert alert-info mt-3">
              Nzyme has not collected database statistics yet. The first snapshot is taken within a minute of
              node startup. This page refreshes automatically.
            </div>
          </div>
        </div>
      </React.Fragment>
    )
  }

  if (!health) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      {menu}

      <div className="row mt-3">
        <div className="col-12">
          <h1>Database Health &amp; Maintenance</h1>
        </div>
      </div>

      <DatabaseSummaryStrip health={health} indicators={indicators} />

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Database Health Indicators" slim={true}
                                     internalLink={ApiRoutes.SYSTEM.HEALTH.INDEX} />
              <DatabaseIndicatorsCard indicators={indicators} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Maintenance Activity" slim={true} />
              <MaintenanceActivity health={health} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Server" slim={true} />
              <DatabaseServerCard server={health.server} collectedAt={health.collected_at} nodeName={health.node_name} />
            </div>
          </div>
        </div>
        <div className="col-xl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Trends" slim={true} />
              <DatabaseTrendsCard />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Server Configuration" slim={true} />
              <DatabaseSettingsTable settings={health.settings} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Tables" slim={true} />
              <DatabaseTablesTable tables={health.tables}
                                   indexes={health.indexes}
                                   reindexThresholdPercent={health.reindex_bloat_threshold_percent}
                                   reindexMinimumMb={health.reindex_minimum_index_mb}
                                   autoReindexEnabled={health.auto_reindex_enabled}
                                   onVacuum={onVacuum}
                                   onReindex={onReindexTable}
                                   busy={health.running_action !== null || health.queued_actions.length > 0} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Indexes" slim={true} />
              <DatabaseIndexesTable indexes={health.indexes}
                                    reindexThresholdPercent={health.reindex_bloat_threshold_percent}
                                    reindexMinimumMb={health.reindex_minimum_index_mb}
                                    autoReindexEnabled={health.auto_reindex_enabled}
                                    onReindex={onReindexIndex}
                                    busy={health.running_action !== null || health.queued_actions.length > 0} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Maintenance Log" slim={true} />
              <MaintenanceLogTable revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
