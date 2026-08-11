import React, {useContext, useEffect, useState} from "react";
import {useLocation} from "react-router-dom";
import usePageTitle from "../../../../util/UsePageTitle";
import {TapContext} from "../../../../App";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS} from "./NATTraversalDiscoveryFilterFields";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {NAT_MENU_ITEMS} from "../NATMenuItems";
import NATTraversalDiscoveryTable from "./NATTraversalDiscoveryTable";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function NATTraversalDiscoveryPage() {

  usePageTitle("NAT Traversal Discoveries");

  const tapContext = useContext(TapContext);
  const urlQuery = useQuery();

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))
  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS)
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
          <SectionMenuBar items={NAT_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.NAT.TRAVERSAL.DISCOVERY.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>NAT Traversal Discoveries</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="All Discoveries"
                                     helpLink="https://go.nzyme.org/nat-traversal-discoveries"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS} />

              <hr />

              <NATTraversalDiscoveryTable timeRange={timeRange}
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