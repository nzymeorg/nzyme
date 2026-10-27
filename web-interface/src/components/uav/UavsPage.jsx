import React, {useContext, useEffect, useState} from "react";
import UavService from "../../services/UavService";
import {Presets} from "../shared/timerange/TimeRange";
import {TapContext} from "../../App";
import {disableTapSelector, enableTapSelector} from "../misc/TapSelector";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import UavsTable from "./UavsTable";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import UavLiveMap from "./UavLiveMap";
import ApiRoutes from "../../util/ApiRoutes";
import {Navigate} from "react-router-dom";
import usePageTitle from "../../util/UsePageTitle";
import {useUrlTimeRange} from "../shared/timerange/TimeRangeSelector";

const uavService = new UavService();

export default function UavsPage() {

  usePageTitle("UAVs");

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [uavs, setUavs] = useState(null);
  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24)
  const [page, setPage] = useState(1);
  const perPage = 25;

  const [uavRedirect, setUavRedirect] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setUavs(null);
    uavService.findAll(setUavs, organizationId, tenantId, timeRange, selectedTaps, perPage, (page-1)*perPage);
  }, [selectedTaps, timeRange, page, organizationId, tenantId, revision]);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const onUavClick = (uav) => {
    setUavRedirect(uav);
  }

  if (uavRedirect) {
    return <Navigate to={ApiRoutes.UAV.DETAILS(uavRedirect.identifier)} />
  }

  return (
      <React.Fragment>

        <div className="row">
          <div className="col-10">
            <h1>Unmanned Aerial Vehicles (UAVs)</h1>
          </div>

          <div className="col-2 text-end">
            <a href="https://go.nzyme.org/uavs" className="btn btn-secondary">Help</a>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="UAVs"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Live Map"
                                       fixedAppliedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <UavLiveMap containerHeight={600} uavs={uavs} onUavClick={onUavClick} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All UAVs"
                                       fixedAppliedTimeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="text-muted mt-0">
                  The table shows the most recent recorded values. More details and a history of values is available on
                  the UAV details pages.
                </p>

                <UavsTable uavs={uavs} page={page} perPage={perPage} setPage={setPage} />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}