import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import usePageTitle from "../../../../../util/UsePageTitle";
import {TapContext} from "../../../../../App";
import {timeRangeFromURLOrDefault} from "../../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../../shared/timerange/TimeRange";
import {queryParametersToFilters} from "../../../../shared/filtering/FilterQueryParameters";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import Filters from "../../../../shared/filtering/Filters";
import {NAT_MENU_ITEMS} from "../../NATMenuItems";
import NATService from "../../../../../services/ethernet/NATService";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "./STUNConnectionsFilterFields";
import STUNConnectionsTable from "./STUNConnectionsTable";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function STUNConnectionsPage() {

  usePageTitle("STUN Connections");

  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))
  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), STUN_CONNECTIONS_FILTER_FIELDS)
  );

  const [histogram, setHistogram] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setHistogram(null);

    //natService.getTraversalDiscoveriesHistogram(timeRange, filters, selectedTaps, setHistogram);
  }, [selectedTaps, timeRange, filters, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={NAT_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     helpLink="https://go.nzyme.org/stun-connections"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={STUN_CONNECTIONS_FILTER_FIELDS} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="All Connections"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <STUNConnectionsTable timeRange={timeRange}
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