import React, {useContext, useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Dot11Service from "../../../../services/Dot11Service";
import {TapContext} from "../../../../App";
import SignalStrength from "../../../shared/SignalStrength";
import InfrastructureTypes from "../../util/InfrastructureTypes";
import WPSInformation from "../../util/WPSInformation";
import SecuritySuites from "../../util/SecuritySuites";
import SSIDAdvertisementHistogram from "./SSIDAdvertisementHistogram";
import SSIDSignalWaterfallChart from "./SSIDSignalWaterfallChart";
import SSIDChannelUsageHistogram from "./SSIDChannelUsageHistogram";
import SSIDAccessPointClients from "./SSIDAccessPointClients";
import numeral from "numeral";
import {dot11FrequencyToChannel} from "../../../../util/Tools";
import ChannelSelector from "../../util/ChannelSelector";
import SSIDMonitoredInformation from "./SSIDMonitoredInformation";
import DiscoHistogram from "../../disco/DiscoHistogram";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";
import TapBasedSignalStrengthTable from "../../../shared/TapBasedSignalStrengthTable";
import Dot11SecurityProtocolList from "../../shared/Dot11SecurityProtocolList";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {Presets} from "../../../shared/timerange/TimeRange";
import usePageTitle from "../../../../util/UsePageTitle";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import {BSSID_MENU_ITEMS} from "../BSSIDMenuItems";
import {SSID_MENU_ITEMS} from "./SSIDMenuItems";
import {timeRangeFromURLOrDefault} from "../../../shared/timerange/TimeRangeUrl";

const dot11Service = new Dot11Service();

function SSIDDetailsPage() {

  const {bssidParam} = useParams();
  const {ssidParam} = useParams();
  const {frequencyParam} = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [selectedFrequency, setSelectedFrequency] = useState(parseInt(frequencyParam, 10));
  const ssidTimeRange = Presets.ALL_TIME;

  const [ssid, setSSID] = useState(null);

  const [advertisementHistogramType, setHistogramAdvertisementType] = useState("beacon_count");
  const [advertisementHistogramTimeRange, setAdvertisementHistogramTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "ssid_advertisements"));
  const [channelUsageHistogramTimeRange, setChannelUsageHistogramTimeRange] = useState(Presets.RELATIVE_MINUTES_15);
  const [signalWaterfallTimeRange, setSignalWaterfallTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  const [discoActivityTimeRange, setDiscoActivityTimeRange] = useState(() => timeRangeFromURLOrDefault(Presets.RELATIVE_HOURS_24, "disco_activity"));

  usePageTitle(ssid ? `SSID: ${ssid.ssid}` : "SSID Details");

  useEffect(() => {
    dot11Service.findSSIDOfBSSID(bssidParam, ssidParam, ssidTimeRange, selectedTaps, (response) => setSSID(response.data));
  }, [selectedTaps])

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  const signalWaterfallTitle = () => {
    return (
        <span>
          Signal Waterfall for Channel {dot11FrequencyToChannel(selectedFrequency)}{' '}
          ({numeral(selectedFrequency).format("0,0")} MHz)
        </span>
    );
  }

  if (!ssid) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.OVERVIEW}>WiFi</a></li>
              <li className="breadcrumb-item"><a href={ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX}>Access Points</a></li>
              <li className="breadcrumb-item">{ssid.bssid.address}</li>
              <li className="breadcrumb-item">SSIDs</li>
              <li className="breadcrumb-item active" aria-current="page">{ssid.ssid}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={SSID_MENU_ITEMS(ssid.bssid.address, ssid.ssid, frequencyParam)}
                          activeRoute={ApiRoutes.DOT11.NETWORKS.SSIDS.DETAILS(ssid.bssid.address, ssid.ssid, frequencyParam)}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>
            Network Details
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="SSID Information"
                                     fixedAppliedTimeRange={ssidTimeRange} />

              <dl className="mb-0">
                <dt>BSSID</dt>
                <dd>
                  <Dot11MacAddress addressWithContext={ssid.bssid}
                                   href={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(ssid.bssid.address)}
                                   withAssetName
                                   showOui={true} />
                </dd>
                <dt>Name</dt>
                <dd>{ssid.ssid}</dd>
                <dt>Signal Strength</dt>
                <dd>
                  <SignalStrength strength={ssid.signal_strength_average} selectedTapCount={selectedTaps.length} />
                </dd>
                <dt>Type</dt>
                <dd><InfrastructureTypes types={ssid.infrastructure_types} /></dd>
                <dt>Rates</dt>
                <dd>{ssid.rates ? ssid.rates.filter(n => n).join(", ") + " Mbps" : "N/A"}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="row">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Security & Encryption"
                                         fixedAppliedTimeRange={ssidTimeRange} />

                  <dl className="mb-0">
                    <dt>Protocol</dt>
                    <dd><Dot11SecurityProtocolList protocols={ssid.security_protocols} /></dd>
                    <dt>WPS</dt>
                    <dd><WPSInformation wps={ssid.is_wps} /></dd>
                    <dt>Suite</dt>
                    <dd><SecuritySuites suites={ssid.security_suites} /></dd>
                  </dl>
                </div>
              </div>
            </div>
          </div>
          <div className="row mt-2">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Fingerprints"
                                         helpLink="https://go.nzyme.org/wifi-fingerprinting"
                                         fixedAppliedTimeRange={ssidTimeRange} />

                  <ul className="mb-0">
                  {ssid.fingerprints.map(function (fp) {
                    return <li key={"fp-" +fp}>{fp}</li>
                  })}
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <SSIDMonitoredInformation ssid={ssid} />

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="SSID Advertisements"
                                     urlKey="ssid_advertisements"
                                     timeRange={advertisementHistogramTimeRange}
                                     setTimeRange={setAdvertisementHistogramTimeRange} />

              <select className="form-select form-select-sm" style={{width: 250}}
                      onChange={(e) => { setHistogramAdvertisementType(e.target.value) }}
                      value={advertisementHistogramType}>
                <option value="beacon_count">Beacon Count</option>
                <option value="proberesp_count">Probe Response Count</option>
              </select>

              <SSIDAdvertisementHistogram bssid={ssid.bssid.address}
                                          ssid={ssid.ssid}
                                          urlKey="ssid_advertisements"
                                          timeRange={advertisementHistogramTimeRange}
                                          setTimeRange={setAdvertisementHistogramTimeRange}
                                          parameter={advertisementHistogramType} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Disconnection Activity"
                                     smallText="All SSIDs"
                                     urlKey="disco_activity"
                                     timeRange={discoActivityTimeRange}
                                     setTimeRange={setDiscoActivityTimeRange} />
              <p className="text-muted">
                All deauthentication and disassociation frames indicating a disconnection from this access point. This
                includes disconnections initiated by access point as well as clients.
              </p>

              <DiscoHistogram discoType="disconnection"
                              timeRange={discoActivityTimeRange}
                              urlKey="disco_activity"
                              setTimeRange={setDiscoActivityTimeRange}
                              bssids={[ssid.bssid.address]} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <div className="row">
            <div className="col-md-8">
              <div className="row">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title="Average Signal Strength"
                                             smallText="All SSIDs, All Channels"
                                             fixedAppliedTimeRange={ssidTimeRange} />

                      <TapBasedSignalStrengthTable strengths={ssid.signal_strength}/>
                    </div>
                  </div>
                </div>
              </div>

              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title="Active Channels"
                                             timeRange={channelUsageHistogramTimeRange}
                                             setTimeRange={setChannelUsageHistogramTimeRange} />

                      <SSIDChannelUsageHistogram bssid={ssid.bssid.address}
                                                 ssid={ssid.ssid}
                                                 timeRange={channelUsageHistogramTimeRange}/>
                    </div>
                  </div>
                </div>
              </div>
              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title={signalWaterfallTitle()}
                                             timeRange={signalWaterfallTimeRange}
                                             setTimeRange={setSignalWaterfallTimeRange} />

                      <ChannelSelector currentFrequency={selectedFrequency}
                                       setFrequency={setSelectedFrequency}
                                       frequencies={ssid.frequencies}/>

                      <SSIDSignalWaterfallChart bssid={ssid.bssid.address}
                                                ssid={ssid.ssid}
                                                frequency={selectedFrequency}
                                                timeRange={signalWaterfallTimeRange} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-md-4">
              <div className="row">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <CardTitleWithControls title="Clients connected to BSSID"
                                             smallText="All SSIDs"
                                             fixedAppliedTimeRange={ssidTimeRange} />

                      <p className="text-muted">
                        Please note that recording clients generally demands closer proximity, as
                        well as more precise matching of transmission-related parameters compared to recording access
                        points. Additionally, many modern devices will randomize their MAC addresses and hide vendor
                        information.
                      </p>

                      <SSIDAccessPointClients clients={ssid.access_point_clients} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}

export default SSIDDetailsPage;