import React from "react";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import AutomaticDot11MacAddressLink from "../../shared/context/macs/AutomaticDot11MacAddressLink";
import SignalStrength from "../../shared/SignalStrength";
import {DISCO_FILTER_FIELDS} from "./DiscoFilterFields";
import {discoTypeToName} from "./DiscoType";
import PivotLink from "../../shared/PivotLink";
import {discoSignalLink} from "./signal/DiscoSignalLinks";

export default function DiscoFramesTable(props) {

  const frames = props.frames;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const setFilters = props.setFilters;
  const orderColumn = props.orderColumn;
  const setOrderColumn = props.setOrderColumn;
  const orderDirection = props.orderDirection;
  const setOrderDirection = props.setOrderDirection;

  const signalTimeRange = props.signalTimeRange;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    if (!setFilters) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters} fields={DISCO_FILTER_FIELDS} field={field} value={value} />
  }

  const reason = (frame) => {
    if (frame.is_protected) {
      return (
          <React.Fragment>
            <span className="text-muted" title="The frame is protected. Its reason code is encrypted." style={{cursor: "help"}}>
              Encrypted
            </span>
            {filterIcon("is_protected", "true")}
          </React.Fragment>
      )
    }

    return (
        <React.Fragment>
          <span title={frame.reason} style={{cursor: "help"}}>{frame.reason_code}</span>
          {filterIcon("reason_code", frame.reason_code)}
        </React.Fragment>
    )
  }

  const signalPivot = (frame) => {
    if (!signalTimeRange) {
      return null;
    }

    return <PivotLink to={discoSignalLink([frame.transmitter.mac.address], signalTimeRange)}
                      title="Signal strength investigation of this transmitter"
                      icon={true}
                      className="ms-2 small" />
  }

  const mac = (link, field, suffix = null) => {
    return <AutomaticDot11MacAddressLink mac={link.mac.address}
                                         type={link.type}
                                         addressWithContext={link.mac}
                                         filterElement={<React.Fragment>{filterIcon(field, link.mac.address)}{suffix}</React.Fragment>} />
  }

  const receiver = (frame) => {
    if (frame.is_broadcast) {
      return (
          <React.Fragment>
            <span className="mac-address" title="Broadcast address. The frame addresses every client of the BSSID.">
              Broadcast
            </span>
            {filterIcon("is_broadcast", "true")}
          </React.Fragment>
      )
    }

    return mac(frame.receiver, "receiver");
  }

  const bssid = (frame) => {
    if (frame.transmitter_is_bssid) {
      return <span className="text-muted" title="The BSSID is the transmitter address. The access point sent this frame.">Transmitter</span>
    }

    if (frame.receiver_is_bssid) {
      return <span className="text-muted" title="The BSSID is the receiver address. This frame was sent to the access point.">Receiver</span>
    }

    return mac(frame.bssid, "bssid",
        <i className="fa-solid fa-triangle-exclamation text-warning ms-1"
           style={{cursor: "help"}}
           title="Neither transmitter nor receiver is the BSSID this frame refers to." />
    );
  }

  const frequency = (frame) => {
    if (!frame.frequency) {
      return <span className="text-muted">Unknown</span>
    }

    return (
        <React.Fragment>
          {frame.frequency} MHz
          {filterIcon("frequency", frame.frequency)}
        </React.Fragment>
    )
  }

  const signal = (frame) => {
    if (frame.signal_strength_average === null || frame.signal_strength_average === undefined) {
      return <span className="text-muted">Unknown</span>
    }

    return (
        <React.Fragment>
          <SignalStrength strength={frame.signal_strength_average}
                          filterElement={filterIcon("signal_strength", Math.round(frame.signal_strength_average))} />
        </React.Fragment>
    )
  }

  if (!frames) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (frames.total === 0) {
    return <div className="alert alert-info mb-0">No disconnection frames recorded.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(frames.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th style={{width: 200}}>Time {columnSorting("created_at")}</th>
            <th>Tap</th>
            <th>Type {columnSorting("disco_type")}</th>
            <th>Transmitter {columnSorting("transmitter")}</th>
            <th>Receiver {columnSorting("receiver")}</th>
            <th>BSSID {columnSorting("bssid")}</th>
            <th>Reason {columnSorting("reason_code")}</th>
            <th>Frequency {columnSorting("frequency")}</th>
            <th>Signal {columnSorting("signal_strength")}</th>
            <th>Frames {columnSorting("frame_count")}</th>
            <th title="Frames that repeat an earlier transmission.">Retries</th>
            <th title="The number of different sequence numbers between this transmitter and receiver within ten seconds.">
              Sequence Numbers
            </th>
          </tr>
          </thead>
          <tbody>
          {frames.frames.map((frame, i) => {
            return (
                <tr key={i}>
                  <td title={moment(frame.timestamp).format()}>{moment(frame.timestamp).format()}</td>
                  <td>{frame.tap_name ? frame.tap_name : <span className="text-muted">Unknown</span>}</td>
                  <td>
                    {discoTypeToName(frame.disco_type)}
                    {filterIcon("disco_type", frame.disco_type)}
                  </td>
                  <td>{mac(frame.transmitter, "transmitter", signalPivot(frame))}</td>
                  <td>{receiver(frame)}</td>
                  <td>{bssid(frame)}</td>
                  <td>{reason(frame)}</td>
                  <td>{frequency(frame)}</td>
                  <td>{signal(frame)}</td>
                  <td>{numeral(frame.frame_count).format("0,0")}</td>
                  <td>{numeral(frame.retry_frames).format("0,0")}</td>
                  <td>{numeral(frame.pair_sequence_numbers).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={frames.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
