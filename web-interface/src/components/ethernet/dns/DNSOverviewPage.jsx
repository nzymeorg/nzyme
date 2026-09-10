import React, {useContext, useEffect, useState} from 'react'
import DNSStatisticsChart from './DNSStatisticsChart'
import DNSNumbers from './DNSNumbers'
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {Presets} from "../../shared/timerange/TimeRange";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import DNSService from "../../../services/ethernet/DNSService";
import DNSContactAttemptsTable from "./DNSContactAttemptsTable";
import DNSEntropyLogTable from "./entropy/DNSEntropyLogTable";
import ApiRoutes from "../../../util/ApiRoutes";
import SectionMenuBar from "../../shared/SectionMenuBar";
import {DNS_MENU_ITEMS} from "./DNSMenuItems";
import usePageTitle from "../../../util/UsePageTitle";

function byteConversion (x) {
  return x / 1024
}

const dnsService = new DNSService()

function DNSOverviewPage () {

  usePageTitle("DNS Overview");

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [queryStats, setQueryStats] = useState(null);
  const [responseStats, setResponseStats] = useState(null);
  const [nxdomainStats, setNxdomainStats] = useState(null);

  const [queryStatsTimeRange, setQueryStatsTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [responseStatsTimeRange, setResponseStatsTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [nxdomainStatsTimeRange, setNxdomainStatsTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [contactAttemptsTimeRange, setContactAttemptsTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [entropyLogTimeRange, setEntropyLogTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setQueryStats(null);
    dnsService.getGlobalChart(queryStatsTimeRange, selectedTaps, "request_bytes", setQueryStats);
  }, [selectedTaps, queryStatsTimeRange]);

  useEffect(() => {
    setResponseStats(null);
    dnsService.getGlobalChart(responseStatsTimeRange, selectedTaps, "response_bytes", setResponseStats);
  }, [selectedTaps, responseStatsTimeRange]);

  useEffect(() => {
    setNxdomainStats(null);
    dnsService.getGlobalChart(nxdomainStatsTimeRange, selectedTaps, "nxdomain_count", setNxdomainStats);
  }, [selectedTaps, nxdomainStatsTimeRange]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={DNS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.DNS.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <DNSNumbers />
        </div>

        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Query Traffic"
                                       timeRange={queryStatsTimeRange}
                                       setTimeRange={setQueryStatsTimeRange} />

                <DNSStatisticsChart data={queryStats}
                                    setTimeRange={setQueryStatsTimeRange}
                                    timeRange={queryStatsTimeRange}
                                    conversion={byteConversion}
                                    valueType="KB"/>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Response Traffic"
                                       timeRange={responseStatsTimeRange}
                                       setTimeRange={setResponseStatsTimeRange} />

                <DNSStatisticsChart data={responseStats}
                                    setTimeRange={setResponseStatsTimeRange}
                                    timeRange={responseStatsTimeRange}
                                    conversion={byteConversion}
                                    valueType="KB"/>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="NXDOMAIN Responses"
                                       helpLink="https://go.nzyme.org/ethernet-dns-nxdomain"
                                       timeRange={nxdomainStatsTimeRange}
                                       setTimeRange={setNxdomainStatsTimeRange} />

                <DNSStatisticsChart data={nxdomainStats}
                                    setTimeRange={setNxdomainStatsTimeRange}
                                    timeRange={nxdomainStatsTimeRange}
                                    attribute="nxdomain_count"/>
              </div>
            </div>
          </div>

        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="DNS Server Contact Attempts"
                                       timeRange={contactAttemptsTimeRange}
                                       setTimeRange={setContactAttemptsTimeRange}/>

                <DNSContactAttemptsTable timeRange={contactAttemptsTimeRange} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="DNS Transactions with Entropy Outliers"
                                       helpLink="https://go.nzyme.org/ethernet-dns-entropy"
                                       timeRange={entropyLogTimeRange}
                                       setTimeRange={setEntropyLogTimeRange}/>

                <DNSEntropyLogTable timeRange={entropyLogTimeRange}  />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )
}

export default DNSOverviewPage
