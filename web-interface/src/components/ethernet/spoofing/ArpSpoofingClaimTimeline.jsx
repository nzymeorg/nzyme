import React from "react";
import Store from "../../../util/Store";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";

export default function ArpSpoofingClaimTimeline({finding, timeline}) {

  if (!timeline || timeline.length === 0) {
    return (
      <div className="alert alert-info mb-0">
        No ARP packets of this address are retained anymore. The timeline is built from the raw ARP packets and
        reaches back only as far as they are kept.
      </div>
    )
  }

  const buckets = timeline.map((b) => new Date(b.bucket));

  const spooferColor = "#d9534f";
  const ownerColor = Store.get("dark_mode") ? "#e6e6e6" : "#1d30d7";

  const traces = [
    {
      name: "Owner " + finding.owner_mac,
      type: "bar",
      x: buckets,
      y: timeline.map((b) => b.owner_claims),
      marker: {color: ownerColor},
      hovertemplate: "%{x}<br>%{y:,} claims by owner<extra></extra>"
    },
    {
      name: "Spoofer " + finding.spoofer_mac,
      type: "bar",
      x: buckets,
      y: timeline.map((b) => b.spoofer_claims),
      marker: {color: spooferColor},
      hovertemplate: "%{x}<br>%{y:,} claims by spoofer<extra></extra>"
    }
  ];

  return (
    <React.Fragment>
      <p className="text-muted">
        How often each device claimed the address over time. Both devices claiming it in the same period is what
        makes this poisoning rather than an address that changed hands.
      </p>

      <SimpleBarChart height={200} finalData={traces} yaxistitle="Claims" />
    </React.Fragment>
  )

}
