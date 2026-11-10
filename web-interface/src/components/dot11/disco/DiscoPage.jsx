import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import {Presets} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import Filters from "../../shared/filtering/Filters";
import useUrlFilters from "../../shared/filtering/useUrlFilters";
import onSaveFiltersAsMonitor from "../../shared/filtering/monitors/SaveMonitorCallback";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import RenderConditionally from "../../misc/RenderConditionally";
import Dot11Service from "../../../services/Dot11Service";
import {DISCO_FILTER_FIELDS} from "./DiscoFilterFields";
import DiscoFramesChart from "./DiscoFramesChart";
import DiscoMacHistogram from "./DiscoMacHistogram";
import DiscoPairsTable from "./DiscoPairsTable";
import DiscoReasonCodesHistogram from "./DiscoReasonCodesHistogram";
import DiscoFrequenciesHistogram from "./DiscoFrequenciesHistogram";
import DiscoFramesTable from "./DiscoFramesTable";

const dot11Service = new Dot11Service();

export default function DiscoPage() {

  usePageTitle("WiFi Disconnections");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(DISCO_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

  const [histogram, setHistogram] = useState(null);
  const [frames, setFrames] = useState(null);

  const [orderColumn, setOrderColumn] = useState("created_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const perPage = 25;
  const [page, setPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    if (!monitorsReady) {
      return;
    }

    setHistogram(null);
    dot11Service.getDiscoHistogram(timeRange, filters, selectedTaps, null, null, setHistogram);
  }, [timeRange, filters, selectedTaps, revision, monitorsReady]);

  useEffect(() => {
    if (!monitorsReady) {
      return;
    }

    setFrames(null);
    dot11Service.findDiscoFrames(organizationId, tenantId, timeRange, filters, selectedTaps, null,
        orderColumn, orderDirection, perPage, (page - 1) * perPage, setFrames);
  }, [organizationId, tenantId, timeRange, filters, selectedTaps, orderColumn, orderDirection, page, revision, monitorsReady]);

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <h1>Disconnection Activity</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Disconnection Frames Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/disco" />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={DISCO_FILTER_FIELDS}
                         monitorType="DOT11_DISCO_FRAME"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("DOT11_DISCO_FRAME", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Disconnection Frames"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-frame-types" />

                  <DiscoFramesChart histogram={histogram} timeRange={timeRange} setTimeRange={setTimeRange} />

                  <p className="mb-0 mt-3 text-muted">
                    Deauthentication and disassociation frames end a connection between a client and an access point.
                  </p>
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Broadcast and Unicast Frames"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-broadcast" />

                  <DiscoFramesChart histogram={histogram} timeRange={timeRange} setTimeRange={setTimeRange} mode="broadcast" />

                  <p className="mb-0 mt-3 text-muted">
                    A frame sent to the broadcast address disconnects every client of an access point at once.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-4">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Transmitters"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-addresses" />

                  <DiscoMacHistogram histogramType="transmitters"
                                     columnTitle="Transmitter"
                                     timeRange={timeRange}
                                     filters={filters}
                                     setFilters={setFilters}
                                     revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-4">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top Receivers"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-addresses" />

                  <DiscoMacHistogram histogramType="receivers"
                                     columnTitle="Receiver"
                                     timeRange={timeRange}
                                     filters={filters}
                                     setFilters={setFilters}
                                     revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-4">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Top BSSIDs"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-addresses" />

                  <DiscoMacHistogram histogramType="bssids"
                                     columnTitle="BSSID"
                                     timeRange={timeRange}
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
                  <CardTitleWithControls title="Top Pairs"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-addresses" />

                  <DiscoPairsTable timeRange={timeRange}
                                   filters={filters}
                                   setFilters={setFilters}
                                   revision={revision} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-7">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Reason Codes"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-reason-codes" />

                  <DiscoReasonCodesHistogram timeRange={timeRange}
                                             filters={filters}
                                             setFilters={setFilters}
                                             revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-5">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Channels"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-channels" />

                  <DiscoFrequenciesHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="Disconnection Frames"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/disco-frames" />

                  <p className="text-muted">
                    Taps combine frames that share type, addresses, reason code and channel within a report
                    interval into one row. The frames column shows how many frames a row stands for.
                  </p>

                  <DiscoFramesTable frames={frames}
                                    page={page}
                                    setPage={setPage}
                                    perPage={perPage}
                                    orderColumn={orderColumn}
                                    orderDirection={orderDirection}
                                    setOrderColumn={setOrderColumn}
                                    setOrderDirection={setOrderDirection}
                                    setFilters={setFilters} />
                </div>
              </div>
            </div>
          </div>
        </RenderConditionally>
      </React.Fragment>
  )

}
