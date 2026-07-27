import React, {useContext, useEffect, useState} from "react";
import {RTSP_FILTER_FIELDS} from "./RTSPFilterFields";
import usePageTitle from "../../../../util/UsePageTitle";
import {useLocation} from "react-router-dom";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {STREAMS_MENU_ITEMS} from "../StreamsMenuItems";
import SSHSessionsTable from "../../remote/ssh/SSHSessionsTable";
import RTSPStreamsTable from "./RTSPStreamsTable";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function RTSPStreamsPage() {

  usePageTitle("RTSP Streams");

  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))
  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), RTSP_FILTER_FIELDS)
  );

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={STREAMS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>RTSP Streams</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="All Streams"
                                     helpLink="https://go.nzyme.org/ethernet-rtsp"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={RTSP_FILTER_FIELDS} />

              <hr />

              <RTSPStreamsTable timeRange={timeRange}
                                filters={filters}
                                setFilters={setFilters}
                                revision={revision} />

            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}