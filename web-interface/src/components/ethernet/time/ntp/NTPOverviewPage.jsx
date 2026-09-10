import React, {useContext, useEffect, useState} from 'react';
import {TapContext} from "../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import {TIME_MENU_ITEMS} from "../TimeMenuItems";
import {Presets} from "../../../shared/timerange/TimeRange";
import {useLocation} from "react-router-dom";
import TimeService from "../../../../services/ethernet/TimeService";
import {queryParametersToFilters} from "../../../shared/filtering/FilterQueryParameters";
import {NTP_FILTER_FIELDS} from "./NTPFilterFields";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import NTPTransactionsTable from "./NTPTransactionsTable";
import Filters from "../../../shared/filtering/Filters";
import NTPTransactionsHistogram from "./NTPTransactionsHistogram";
import NTPClientRequestResponseRatioHistogram from "./NTPClientRequestResponseRatioHistogram";
import NTPTopServersHistogram from "./NTPTopServersHistogram";
import usePageTitle from "../../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeSelector";

const timeService = new TimeService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export function NTPOverviewPage() {

  usePageTitle("NTP Transactions");

  const urlQuery = useQuery();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24))

  const [transactionsHistogram, setTransactionsHistogram] = useState(null);

  const [revision, setRevision] = useState(new Date());

  const [filters, setFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), NTP_FILTER_FIELDS)
  );

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTransactionsHistogram(null);

    timeService.getNTPTransactionsHistogram(timeRange, filters, selectedTaps, setTransactionsHistogram);
  }, [selectedTaps, timeRange, filters, revision]);

  return (
    <>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={TIME_MENU_ITEMS} activeRoute={ApiRoutes.ETHERNET.TIME.NTP.INDEX}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <Filters filters={filters}
                       setFilters={setFilters}
                       fields={NTP_FILTER_FIELDS} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Transactions"
                                     fixedTimeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())}/>

              <NTPTransactionsHistogram histogram={transactionsHistogram} timeRange={timeRange} setTimeRange={setTimeRange} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Top Servers"
                                     fixedTimeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())}/>

              <NTPTopServersHistogram filters={filters}
                                      setFilters={setFilters}
                                      timeRange={timeRange}
                                      revision={revision} />
            </div>
          </div>
        </div>
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Client Request/Response Ratio"
                                     fixedTimeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())}/>

              <NTPClientRequestResponseRatioHistogram filters={filters}
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
              <CardTitleWithControls title="Transactions"
                                     fixedTimeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())}/>

              <NTPTransactionsTable filters={filters}
                                    setFilters={setFilters}
                                    timeRange={timeRange}
                                    revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </>
  )

}