import React, {useEffect, useState} from "react";
import numeral from "numeral";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import ReconService from "../../../services/ethernet/ReconService";
import {RECON_MENU_ITEMS} from "./ReconMenuItems";
import ReconTargetsTable from "./ReconTargetsTable";
import {Presets} from "../../shared/timerange/TimeRange";

const reconService = new ReconService();

export default function ReconTargetsPage() {

  usePageTitle("Ethernet Recon Targets");

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("failed_port_count");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [targets, setTargets] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  useEffect(() => {
    setTargets(null);
    reconService.findProbedTargets(
      organizationId, tenantId, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, setTargets
    );
  }, [page, timeRange, orderColumn, orderDirection, organizationId, tenantId, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <SectionMenuBar items={RECON_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.RECON.TARGETS.INDEX}/>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/ethernet-recon-targets" className="btn btn-secondary float-end">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Probed Targets"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Every host that any device probed, regardless of whether that device crossed a scan threshold. A
                host with many unanswered ports and many probing devices is being mapped by a distributed scan
                that no single scanner reveals. The time range cannot reach further back than the slow scan memory.
              </p>

              {targets ? <p className="mb-3"><strong>Total:</strong> {numeral(targets.total).format("0,0")}</p> : null}

              <ReconTargetsTable targets={targets}
                                 page={page}
                                 setPage={setPage}
                                 perPage={perPage}
                                 orderColumn={orderColumn}
                                 setOrderColumn={applyOrderColumn}
                                 orderDirection={orderDirection}
                                 setOrderDirection={applyOrderDirection} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
