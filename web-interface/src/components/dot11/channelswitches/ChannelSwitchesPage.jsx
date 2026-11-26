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
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {CHANNEL_SWITCH_FILTER_FIELDS} from "./ChannelSwitchFilterFields";
import {CHANNEL_SWITCH_MENU_ITEMS} from "./ChannelSwitchMenuItems";
import ChannelSwitchFramesHistogram from "./ChannelSwitchFramesHistogram";
import ChannelSwitchHistogram from "./ChannelSwitchHistogram";
import ChannelSwitchAnnouncements from "./ChannelSwitchAnnouncements";

export default function ChannelSwitchesPage() {

  usePageTitle("WiFi Channel Switches");

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [filters, setFilters] = useUrlFilters(CHANNEL_SWITCH_FILTER_FIELDS);
  const [monitorsReady, setMonitorsReady] = useState(false);

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
            <SectionMenuBar items={CHANNEL_SWITCH_MENU_ITEMS} activeRoute={ApiRoutes.DOT11.CHANNEL_SWITCHES.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Channel Switch Announcement Filters"
                                       timeRange={timeRange}
                                       setTimeRange={setTimeRange}
                                       helpLink="https://go.nzyme.org/channel-switches" />

                <Filters filters={filters}
                         setFilters={setFilters}
                         fields={CHANNEL_SWITCH_FILTER_FIELDS}
                         monitorType="DOT11_CHANNEL_SWITCH"
                         onMonitorsReady={() => setMonitorsReady(true)}
                         onSaveAsMonitor={onSaveFiltersAsMonitor("DOT11_CHANNEL_SWITCH", organizationId, tenantId)} />
              </div>
            </div>
          </div>
        </div>

        <RenderConditionally render={monitorsReady}>
          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Announcement Frames"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/channel-switches-anomalies" />

                  <ChannelSwitchFramesHistogram timeRange={timeRange}
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
                  <CardTitleWithControls title="Top BSSIDs"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/channel-switches-recording" />

                  <ChannelSwitchHistogram histogramType="bssids"
                                          timeRange={timeRange}
                                          filters={filters}
                                          setFilters={setFilters}
                                          revision={revision} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Announced Channels"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/channel-switches-channels" />

                  <ChannelSwitchHistogram histogramType="channels"
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
                  <CardTitleWithControls title="Announcements"
                                         timeRange={timeRange}
                                         refreshAction={() => setRevision(new Date())}
                                         helpLink="https://go.nzyme.org/channel-switches-outcome" />

                  <ChannelSwitchAnnouncements timeRange={timeRange}
                                              filters={filters}
                                              setFilters={setFilters}
                                              revision={revision} />
                </div>
              </div>
            </div>
          </div>
        </RenderConditionally>
      </React.Fragment>
  )

}
