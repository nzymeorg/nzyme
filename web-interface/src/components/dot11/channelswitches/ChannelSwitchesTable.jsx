import React from "react";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import AutomaticDot11MacAddressLink from "../../shared/context/macs/AutomaticDot11MacAddressLink";
import SignalStrength from "../../shared/SignalStrength";
import {dot11FrequencyToChannel} from "../../../util/Tools";
import {CHANNEL_SWITCH_FILTER_FIELDS} from "./ChannelSwitchFilterFields";
import {channelSwitchFrameTypeToName, channelSwitchOutcomeToName} from "./ChannelSwitchNames";
import ChannelSwitchAnomalies from "./ChannelSwitchAnomalies";
import PivotLink from "../../shared/PivotLink";
import {discoAnomalySignalTimeRange} from "../disco/signal/DiscoSignalLinks";
import {channelSwitchSignalLink} from "./ChannelSwitchSignalLinks";

export default function ChannelSwitchesTable(props) {

  const announcements = props.announcements;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setFilters = props.setFilters;
  const orderColumn = props.orderColumn;
  const setOrderColumn = props.setOrderColumn;
  const orderDirection = props.orderDirection;
  const setOrderDirection = props.setOrderDirection;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={CHANNEL_SWITCH_FILTER_FIELDS} field={field} value={value} />
  }

  const from = (cs) => {
    if (!cs.frequency) {
      return <span className="text-muted">Unknown</span>
    }

    return (
        <span title={cs.frequency + " MHz"}>
          {dot11FrequencyToChannel(cs.frequency)}
          {filterIcon("frequency", cs.frequency)}
        </span>
    )
  }

  const to = (cs) => {
    return (
        <span title={cs.new_frequency ? cs.new_frequency + " MHz" : null}>
          {cs.new_channel}
          {filterIcon("new_channel", cs.new_channel)}
        </span>
    )
  }

  const count = (cs) => {
    if (cs.count_min === cs.count_max) {
      return cs.count_min;
    }

    return cs.count_max + " to " + cs.count_min;
  }

  const signal = (cs) => {
    if (cs.signal_strength_average === null || cs.signal_strength_average === undefined) {
      return <span className="text-muted">Unknown</span>
    }

    // Every row is what one tap recorded, so its signal strength is valid with any number of taps selected.
    return (
        <React.Fragment>
          <SignalStrength strength={cs.signal_strength_average}
                          selectedTapCount={1}
                          filterElement={filterIcon("signal_strength", Math.round(cs.signal_strength_average))} />
          <PivotLink to={channelSwitchSignalLink([cs.bssid.mac.address],
                             discoAnomalySignalTimeRange(cs.timestamp, cs.timestamp))}
                     title="Signal strength investigation of this access point"
                     icon={true}
                     className="ms-2 small" />
        </React.Fragment>
    )
  }

  const outcome = (cs) => {
    if (!cs.outcome) {
      return <span className="text-muted">Pending</span>
    }

    if (cs.outcome === "UNKNOWN") {
      return (
          <React.Fragment>
            <span className="text-muted">Unknown</span>
            {filterIcon("outcome", cs.outcome)}
          </React.Fragment>
      )
    }

    return (
        <React.Fragment>
          {channelSwitchOutcomeToName(cs.outcome)}
          {filterIcon("outcome", cs.outcome)}
        </React.Fragment>
    )
  }

  if (!announcements) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (announcements.total === 0) {
    return (
        <div className="alert alert-info mb-0">
          {props.emptyText ? props.emptyText : "No channel switch announcements recorded."}
        </div>
    )
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(announcements.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th className="text-nowrap">Time {columnSorting("created_at")}</th>
            <th className="text-nowrap">BSSID {columnSorting("bssid")}</th>
            <th className="text-nowrap">Frame {columnSorting("frame_type")}</th>
            <th className="text-nowrap">Channel {columnSorting("frequency")}</th>
            <th className="text-nowrap">Announced Channel {columnSorting("new_channel")}</th>
            <th className="text-nowrap">Countdown {columnSorting("count")}</th>
            <th className="text-nowrap">Frames {columnSorting("frame_count")}</th>
            <th className="text-nowrap">Peak Rate {columnSorting("peak_rate")}</th>
            <th className="text-nowrap">Signal {columnSorting("signal_strength")}</th>
            <th>Anomalies {columnSorting("anomalies")}</th>
            <th className="text-nowrap">Outcome {columnSorting("outcome")}</th>
            <th>Tap</th>
          </tr>
          </thead>
          <tbody>
          {announcements.announcements.map((cs) => {
            return (
                <tr key={cs.id}>
                  <td className="text-nowrap" title={moment(cs.timestamp).format()}>{moment(cs.timestamp).format()}</td>
                  <td className="text-nowrap">
                    <AutomaticDot11MacAddressLink mac={cs.bssid.mac.address}
                                                  type={cs.bssid.type}
                                                  addressWithContext={cs.bssid.mac}
                                                  filterElement={filterIcon("bssid", cs.bssid.mac.address)} />
                  </td>
                  <td>
                    {channelSwitchFrameTypeToName(cs.frame_type)}
                    {filterIcon("frame_type", cs.frame_type)}
                  </td>
                  <td className="text-nowrap">{from(cs)}</td>
                  <td className="text-nowrap">{to(cs)}</td>
                  <td className="text-nowrap">{count(cs)}</td>
                  <td>{numeral(cs.frame_count).format("0,0")}</td>
                  <td className="text-nowrap">{numeral(cs.peak_frames_per_second).format("0,0")}/s</td>
                  <td className="text-nowrap">{signal(cs)}</td>
                  <td><ChannelSwitchAnomalies anomalies={cs.anomalies} /></td>
                  <td className="text-nowrap">{outcome(cs)}</td>
                  <td>{cs.tap_name ? cs.tap_name : <span className="text-muted">Unknown</span>}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={announcements.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
