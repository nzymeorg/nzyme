import React, {useContext, useEffect, useState} from "react";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import SOCKSTunnelsTable from "./SOCKSTunnelsTable";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import {SOCKS_FILTER_FIELDS} from "./SOCKSFilterFields";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {useLocation} from "react-router-dom";
import Filters from "../../../shared/filtering/Filters";
import {TUNNELS_MENU_ITEMS} from "../TunnelsMenuItems";
import ApiRoutes from "../../../../util/ApiRoutes";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import usePageTitle from "../../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";
import SOCKSTopServersHistogram from "./SOCKSTopServersHistogram";
import SOCKSTopClientsHistogram from "./SOCKSTopClientsHistogram";
import SOCKSActiveTunnelsHistogram from "./SOCKSActiveTunnelsHistogram";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function SOCKSTunnelsPage() {

  usePageTitle("SOCKS Tunnels");

  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));
  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), SOCKS_FILTER_FIELDS)
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
            <SectionMenuBar items={TUNNELS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Filters"
                                       helpLink="https://go.nzyme.org/ethernet-socks"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={SOCKS_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Active Tunnels"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <SOCKSActiveTunnelsHistogram timeRange={timeRange}
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
                <CardTitleWithControls title="Top Servers"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <SOCKSTopServersHistogram timeRange={timeRange}
                                          setTimeRange={setTimeRange}
                                          filters={filters}
                                          setFilters={setFilters}
                                          revision={revision} />

              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Clients"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <SOCKSTopClientsHistogram timeRange={timeRange}
                                          setTimeRange={setTimeRange}
                                          filters={filters}
                                          setFilters={setFilters}
                                          revision={revision} />

              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Tunnels"
                                       timeRange={timeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <SOCKSTunnelsTable timeRange={timeRange}
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