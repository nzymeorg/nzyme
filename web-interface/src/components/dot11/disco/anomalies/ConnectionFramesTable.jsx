import React from "react";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import AutomaticDot11MacAddressLink from "../../../shared/context/macs/AutomaticDot11MacAddressLink";
import SignalStrength from "../../../shared/SignalStrength";
import {connectionFrameTypeToName} from "./DiscoAnomalyTypes";

export default function ConnectionFramesTable({frames, page, setPage, perPage}) {

  const mac = (link) => {
    return <AutomaticDot11MacAddressLink mac={link.mac.address} type={link.type} addressWithContext={link.mac} />
  }

  const status = (frame) => {
    if (frame.status_code === null || frame.status_code === undefined) {
      return <span className="text-muted">None</span>
    }

    return frame.status_code === 0 ? "0 (Success)" : frame.status_code;
  }

  if (!frames) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (frames.total === 0) {
    return <div className="alert alert-info mb-0">No connection frames recorded.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(frames.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th style={{width: 200}}>Time</th>
            <th>Type</th>
            <th>Transmitter</th>
            <th>Receiver</th>
            <th>Status</th>
            <th>Signal</th>
            <th>Frames</th>
            <th>Retries</th>
          </tr>
          </thead>
          <tbody>
          {frames.frames.map((frame) => {
            return (
                <tr key={frame.id}>
                  <td title={moment(frame.first_seen).format()}>{moment(frame.first_seen).format("YYYY-MM-DD HH:mm:ss.SSS")}</td>
                  <td>{connectionFrameTypeToName(frame.frame_type)}</td>
                  <td>{mac(frame.transmitter)}</td>
                  <td>{mac(frame.receiver)}</td>
                  <td>{status(frame)}</td>
                  <td>
                    {frame.signal_strength_average === null || frame.signal_strength_average === undefined
                        ? <span className="text-muted">Unknown</span>
                        : <SignalStrength strength={frame.signal_strength_average} selectedTapCount={1} />}
                  </td>
                  <td>{numeral(frame.frame_count).format("0,0")}</td>
                  <td>{numeral(frame.retry_frames).format("0,0")}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={frames.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
