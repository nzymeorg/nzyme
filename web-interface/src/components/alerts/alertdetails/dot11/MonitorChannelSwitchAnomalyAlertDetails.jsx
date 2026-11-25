import React, {useMemo} from "react";
import moment from "moment";
import numeral from "numeral";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import {dot11FrequencyToChannel} from "../../../../util/Tools";
import {Absolute} from "../../../shared/timerange/TimeRange";
import {serializeTimeRange} from "../../../shared/timerange/TimeRangeUrl";
import {filteredLink, macFilter} from "../../../ethernet/assets/details/AssetProfileHelpers";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import ChannelSwitchAnomalies from "../../../dot11/channelswitches/ChannelSwitchAnomalies";
import ChannelSwitchAnnouncements from "../../../dot11/channelswitches/ChannelSwitchAnnouncements";
import {channelSwitchFrameTypeToName} from "../../../dot11/channelswitches/ChannelSwitchNames";

// An announcement that was not followed is judged minutes after it was recorded, or later after an outage.
const LEAD_MINUTES = 15;

function MonitorChannelSwitchAnomalyAlertDetails(props) {

  const alert = props.alert;
  const attributes = alert.attributes;

  const anomalies = attributes.anomalies ? attributes.anomalies.split(",") : [];
  const peakRate = parseInt(attributes.peak_frames_per_second, 10);
  const burstThreshold = parseInt(attributes.burst_threshold, 10);
  const frequency = parseInt(attributes.frequency, 10);

  const timeRange = useMemo(() => {
    const announcedAt = moment(attributes.announced_at);
    const from = moment(alert.created_at).subtract(LEAD_MINUTES, "minutes");

    return Absolute(
        (announcedAt.isValid() && announcedAt.isBefore(from) ? announcedAt.subtract(1, "minutes") : from).toDate(),
        moment(alert.last_seen).add(1, "minutes").toDate()
    );
  }, [alert.created_at, alert.last_seen, attributes.announced_at]);

  const channel = () => {
    if (!frequency) {
      return <span className="text-muted">Unknown</span>
    }

    return (
        <React.Fragment>
          {dot11FrequencyToChannel(frequency)}{' '}
          ({numeral(frequency).format("0,0")} MHz)
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Affected BSSID</dt>
                  <dd>
                    <Link to={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(attributes.bssid)}>{attributes.bssid}</Link>
                  </dd>
                  <dt>Channel</dt>
                  <dd>{channel()}</dd>
                  <dt>Announced Channel</dt>
                  <dd>{attributes.new_channel}</dd>
                  <dt>Frame Type</dt>
                  <dd>{channelSwitchFrameTypeToName(attributes.frame_type)}</dd>
                  <dt>Anomalies</dt>
                  <dd><ChannelSwitchAnomalies anomalies={anomalies} /></dd>
                  <dt>Peak Rate</dt>
                  <dd>
                    {numeral(peakRate).format("0,0")} frames per second{' '}
                    <span className="text-muted">
                      (Threshold: {numeral(burstThreshold).format("0,0")})
                    </span>
                  </dd>
                  {attributes.not_followed === "true" ?
                      <React.Fragment>
                        <dt>Announcement Followed</dt>
                        <dd>No, the access point stayed on its channel.</dd>
                      </React.Fragment> : null}
                </dl>

                <p className="mb-0 mt-2">
                  Channel switch announcements for a monitored access point do not look like the access point sent
                  them. Forged announcements move or disconnect clients, also on networks that protect their
                  management frames.
                </p>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Possible false-positives</h3>

                <p>
                  These circumstances can lead to false-positive alerts of this type:
                </p>

                <ul className="mb-0">
                  <li>Two or more access points use the same BSSID.</li>
                  <li>An access point restarted shortly before it announced a switch.</li>
                  <li>The threshold of the channel switch monitor is lower than the beacon rate of its access
                    points.</li>
                  <li>An access point announced a switch and cancelled it, or returned to its channel within
                    minutes.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        {!attributes.bssid ? null :
        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Announcements"
                                       timeRange={timeRange}
                                       internalLink={filteredLink(ApiRoutes.DOT11.CHANNEL_SWITCHES.INDEX,
                                           macFilter("bssid", attributes.bssid)) + "&tr_timerange="
                                           + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange)))}
                                       helpLink="https://go.nzyme.org/channel-switches" />

                <ChannelSwitchAnnouncements timeRange={timeRange}
                                            bssids={[attributes.bssid]}
                                            taps="*"
                                            perPage={10} />
              </div>
            </div>
          </div>
        </div>
        }
      </React.Fragment>
  )

}

export default MonitorChannelSwitchAnomalyAlertDetails;
