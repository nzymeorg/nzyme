import React from "react";
import numeral from "numeral";
import {dot11FrequencyToChannel} from "../../../util/Tools";
import FrameSignalChartLegend from "./FrameSignalChartLegend";

export function frameSignalChannel(frequency) {
  return dot11FrequencyToChannel(frequency) + " (" + numeral(frequency).format("0,0") + " MHz)";
}

export function frameSignalChartTitle(chart) {
  return (chart.tap_name ? chart.tap_name : "Unknown Tap") + ", Channel " + frameSignalChannel(chart.frequency);
}

export default function FrameSignalChartNotes({chart, charts, sources}) {

  const withoutReference = chart.references.filter((reference) => reference.buckets.length === 0)
      .map((reference) => sources.find((source) => source.address === reference.source))
      .filter((source) => source);

  return (
      <React.Fragment>
        <FrameSignalChartLegend sources={sources} />

        <p className="text-muted mb-0 mt-2">
          The tap recorded {numeral(chart.frame_count).format("0,0")} {chart.frame_count === 1 ? "frame" : "frames"} on
          this channel.
          {chart.is_truncated ?
              ` The chart shows the first ${numeral(charts.max_frame_records_per_chart).format("0,0")} records.` : ""}
          {charts.is_truncated ?
              ` Only the latest ${numeral(charts.max_frame_records).format("0,0")} records of the selected charts `
              + `were loaded.` : ""}
          {withoutReference.map((source) => {
            return ` It recorded no ${source.type === "ACCESS_POINT" ? "beacons" : "other frames"} of `
                + `${source.address} on this channel.`;
          }).join("")}
        </p>
      </React.Fragment>
  )

}
