import {useLocation} from "react-router-dom";
import usePageTitle from "../../../../util/UsePageTitle";
import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeUrl";
import {Presets} from "../../../shared/timerange/TimeRange";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {STREAMS_MENU_ITEMS} from "../StreamsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import {WEBRTC_FILTER_FIELDS} from "./WebRTCFilterFields";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import WebRTCSessionsTable from "./WebRTCSessionsTable";
import WebRTCActiveSessionsHistogram from "./WebRTCActiveSessionsHistogram";
import WebRTCTopPeerAddressPairHistogram from "./WebRTCTopPeerAddressPairHistogram";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function WebRTCSessionsPage() {

  usePageTitle("WebRTC Sessions");

  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))
  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), WEBRTC_FILTER_FIELDS)
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
                          activeRoute={ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/ethernet-webrtc"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={WEBRTC_FILTER_FIELDS} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Active Sessions"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <WebRTCActiveSessionsHistogram timeRange={timeRange}
                                             setTimeRange={setTimeRange}
                                             filters={filters}
                                             revision={revision} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Address Pairs (by Bytes Exchanged)"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <WebRTCTopPeerAddressPairHistogram timeRange={timeRange}
                                                 setTimeRange={setTimeRange}
                                                 filters={filters}
                                                 revision={revision} />

            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="All Sessions"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <WebRTCSessionsTable timeRange={timeRange}
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