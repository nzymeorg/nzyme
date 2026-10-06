import React, {useState} from "react";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {useLocation} from "react-router-dom";
import L4SessionsTable from "./L4SessionsTable";
import {L4_SESSIONS_FILTER_FIELDS} from "./L4SessionFilterFields";
import L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram from "./L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram";
import L4SessionsTopDestinationPortsHistogram from "./L4SessionsTopDestinationPortsHistogram";
import L4SessionsTopTrafficSourceAddressesHistogram from "./L4SessionsTopTrafficSourceAddressesHistogram";
import L4SessionsTopTrafficDestinationAddressesHistogram from "./L4SessionsTopTrafficDestinationAddressesHistogram";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeSelector";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {L4_MENU_ITEMS} from "./L4MenuItems";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function L4SessionsPage() {

  usePageTitle("TCP/UDP Sessions");

  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24));
  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
      queryParametersToFilters(urlQuery.get("filters"), L4_SESSIONS_FILTER_FIELDS)
  );

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={L4_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.L4.SESSIONS} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Session Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={L4_SESSIONS_FILTER_FIELDS} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Source Addresses"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTopTrafficSourceAddressesHistogram filters={filters}
                                                              setFilters={setFilters}
                                                              timeRange={timeRange}
                                                              revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Destination Addresses"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTopTrafficDestinationAddressesHistogram filters={filters}
                                                                   setFilters={setFilters}
                                                                   timeRange={timeRange}
                                                                   revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Destination Ports"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="help-text">Sorted by traffic bytes.</p>

                <L4SessionsTopDestinationPortsHistogram filters={filters}
                                                        setFilters={setFilters}
                                                        timeRange={timeRange}
                                                        revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Least Common Non-Ephemeral Destination Ports"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="help-text">Sorted by session count.</p>

                <L4SessionsLeastCommonNonEphemeralDestinationPortsHistogram filters={filters}
                                                                            setFilters={setFilters}
                                                                            timeRange={timeRange}
                                                                            revision={revision} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Sessions"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       refreshAction={() => setRevision(new Date())} />

                <L4SessionsTable filters={filters}
                                 timeRange={timeRange}
                                 setFilters={setFilters}
                                 revision={revision} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}