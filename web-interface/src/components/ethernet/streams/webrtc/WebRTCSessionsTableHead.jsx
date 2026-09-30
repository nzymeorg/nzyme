import React from "react";

export default function WebRTCSessionsTableHead({columnSorting = undefined}) {

  // IMPORTANT: This component is also used on the STUN connection details page.

  return (
    <tr>
      <th>&nbsp; {columnSorting ? columnSorting("is_active") : null}</th>
      <th>ID</th>
      <th>Peer A MAC {columnSorting ? columnSorting("source_mac") : null}</th>
      <th>Peer A Address {columnSorting ? columnSorting("source_address") : null}</th>
      <th>Peer B MAC {columnSorting ? columnSorting("destination_mac") : null}</th>
      <th>Peer B Address {columnSorting ? columnSorting("destination_address") : null}</th>
      <th className="hide-narrow">Streams {columnSorting ? columnSorting("stream_count") : null}</th>
      <th className="hide-narrow">RTP {columnSorting ? columnSorting("has_rtp") : null}</th>
      <th className="hide-narrow">DTLS {columnSorting ? columnSorting("has_dtls") : null}</th>
      <th className="hide-narrow">Audio {columnSorting ? columnSorting("has_audio") : null}</th>
      <th className="hide-narrow">Video {columnSorting ? columnSorting("has_video") : null}</th>
      <th>Bytes {columnSorting ? columnSorting("bytes") : null}</th>
      <th>Duration {columnSorting ? columnSorting("duration") : null}</th>
      <th>Initiated At {columnSorting ? columnSorting("initiated_at") : null}</th>
      <th>Last Activity {columnSorting ? columnSorting("last_activity") : null}</th>
    </tr>
  )

}