import React, {useContext, useEffect, useState} from "react";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import L4Service from "../../../services/ethernet/L4Service";
import {TapContext} from "../../../App";
import L4SessionsTotalBytesChart from "./L4SessionsTotalBytesChart";
import L4SessionsTotalSessionsChart from "./L4SessionsTotalSessionsChart";
import L4SessionsInternalSessionsChart from "./L4SessionsInternallSessionsChart";
import L4SessionsInternalBytesChart from "./L4SessionsInternalBytesChart";
import L4SessionsNumbers from "./L4SessionsNumbers";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {L4_MENU_ITEMS} from "./L4MenuItems";

const l4Service = new L4Service();

export default function L4OverviewPage() {

  usePageTitle("TCP/UDP Overview");

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [statistics, setStatistics] = useState(null);

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    setStatistics(null);
    l4Service.getSessionsStatistics(timeRange, selectedTaps, setStatistics);
  }, [selectedTaps, timeRange, revision]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={L4_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.L4.OVERVIEW} />
          </div>
        </div>

        <L4SessionsNumbers statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Bytes Transferred"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTotalBytesChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Internal Bytes Transferred"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsInternalBytesChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Sessions/Conversations"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTotalSessionsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Internal Sessions/Conversations"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsInternalSessionsChart statistics={statistics} timeRange={timeRange} setTimeRange={setTimeRange} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}