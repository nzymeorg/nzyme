import React, {useContext, useEffect, useState} from 'react';
import {TapContext} from "../../../App";
import {Presets} from "../../shared/timerange/TimeRange";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import L4Service from "../../../services/ethernet/L4Service";
import L4SessionsTotalBytesChart from "../l4/L4SessionsTotalBytesChart";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import ApiRoutes from "../../../util/ApiRoutes";
import L4SessionsTopTrafficDestinationMacsHistogram from "../l4/L4SessionsTopTrafficDestinationMacsHistogram";
import L4SessionsTopTrafficDestinationAddressesHistogram from "../l4/L4SessionsTopTrafficDestinationAddressesHistogram";

const l4Service = new L4Service();

export default function AssetDetailsL4Histograms({title, filters}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timerange, setTimerange] = useState(Presets.RELATIVE_HOURS_24);
  const [revision, setRevision] = useState(new Date());

  const [statistics, setStatistics] = useState(null);

  useEffect(() => {
    setStatistics(null);
    l4Service.getSessionsStatistics(timerange, selectedTaps, setStatistics);
  }, [selectedTaps, timerange, revision, filters]);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const onRefresh = () => {
    setRevision(new Date());
  }

  if (!statistics) {
    return <LoadingSpinner />
  }

  return (
      <>
        <CardTitleWithControls title={title}
                               doNotPersistTimeRange={true}
                               timeRange={timerange}
                               setTimeRange={setTimerange}
                               internalLink={ApiRoutes.ETHERNET.L4.SESSIONS + "?filters=" + JSON.stringify(filters)}
                               refreshAction={onRefresh} />

        <div className="row mt-3 card-container">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Destination MACs/Assets" slim={true}/>

                <L4SessionsTopTrafficDestinationMacsHistogram filters={filters}
                                                              timeRange={timerange}
                                                              revision={revision} />
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Top Traffic Destination Addresses" slim={true}/>

                <L4SessionsTopTrafficDestinationAddressesHistogram filters={filters}
                                                                   timeRange={timerange}
                                                                   revision={revision} />
              </div>
            </div>
          </div>
        </div>
      </>
  )

}