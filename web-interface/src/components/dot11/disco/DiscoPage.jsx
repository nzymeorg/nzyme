import React, {createContext, useContext, useEffect, useState} from "react";
import DiscoHistogram from "./DiscoHistogram";
import DiscoSendersTable from "./DiscoSendersTable";
import DiscoReceiversTable from "./DiscoReceiversTable";
import DiscoPairsTable from "./DiscoPairsTable";
import MonitoredNetworkSelector from "../../shared/MonitoredNetworkSelector";
import {useLocation} from "react-router-dom";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {TapContext} from "../../../App";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {timeRangeFromURLOrDefault} from "../../shared/timerange/TimeRangeUrl";

export const MonitoredNetworkContext = createContext(null);

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

function DiscoPage() {

  usePageTitle("Wifi Disconnections");

  const tapContext = useContext(TapContext);

  const urlQuery = useQuery();

  const [monitoredNetwork, setMonitoredNetwork] = useState(
      urlQuery.get("monitored-network-id") ? urlQuery.get("monitored-network-id") : ""
  );

  const [discoHistogramTimeRange, setDiscoHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "disco"));
  const [deauthHistogramTimeRange, setDeauthHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "deauth"));
  const [disassocHistogramTimeRange, setDisassocHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "disassoc"));
  const [pairsTimeRange, setPairsTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [sendersTimeRange, setSendersTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [receiversTimeRange, setReceiversTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);
  
  return (
      <React.Fragment>
        <MonitoredNetworkContext.Provider value={{network: monitoredNetwork, setNetwork: setMonitoredNetwork}} >
          <div className="row">
            <div className="col-md-3">
              <h1>Disconnection Activity</h1>
            </div>

            <div className="col-md-9">
              <div className="float-end">
                <a href="https://go.nzyme.org/disco" className="btn btn-secondary">Help</a>
              </div>
            </div>
          </div>

          <div className="row">
            <div className="col-md-12">
              <MonitoredNetworkSelector />
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Disconnection Frames Observed"
                                         urlKey="disco"
                                         timeRange={discoHistogramTimeRange}
                                         setTimeRange={setDiscoHistogramTimeRange} />

                  <DiscoHistogram discoType="disconnection"
                                  urlKey="disco"
                                  timeRange={discoHistogramTimeRange}
                                  setTimeRange={setDiscoHistogramTimeRange} />

                  <p className="mb-0 mt-3 text-muted">
                    <i>Disconnection activity</i> refers to the sum of deauthentication and disassociation frames.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Deauthentication Frames Observed"
                                         urlKey="deauth"
                                         timeRange={deauthHistogramTimeRange}
                                         setTimeRange={setDeauthHistogramTimeRange} />

                  <DiscoHistogram discoType="deauthentication"
                                  urlKey="deauth"
                                  timeRange={deauthHistogramTimeRange}
                                  setTimeRange={setDeauthHistogramTimeRange} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Disassociation Frames Observed"
                                         urlKey="disassoc"
                                         timeRange={disassocHistogramTimeRange}
                                         setTimeRange={setDisassocHistogramTimeRange} />

                  <DiscoHistogram discoType="disassociation"
                                  urlKey="disassoc"
                                  timeRange={disassocHistogramTimeRange}
                                  setTimeRange={setDisassocHistogramTimeRange} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Pairs"
                                         timeRange={pairsTimeRange}
                                         setTimeRange={setPairsTimeRange} />

                  <DiscoPairsTable timeRange={pairsTimeRange} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Senders"
                                         timeRange={sendersTimeRange}
                                         setTimeRange={setSendersTimeRange} />

                  <DiscoSendersTable timeRange={sendersTimeRange} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Receivers"
                                         timeRange={receiversTimeRange}
                                         setTimeRange={setReceiversTimeRange} />

                  <DiscoReceiversTable timeRange={receiversTimeRange} />
                </div>
              </div>
            </div>
          </div>
        </MonitoredNetworkContext.Provider>
      </React.Fragment>
  )

}

export default DiscoPage;