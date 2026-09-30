import React from "react";

export default function STUNConnectionsTableHead({columnSorting = undefined}) {

  // IMPORTANT: This component is also used on the WebRTC session details page.

  return (
    <tr>
      <th>{columnSorting ? columnSorting("successful") : null}</th>
      <th>ID</th>
      <th>Source MAC {columnSorting ? columnSorting("source_mac") : null}</th>
      <th>Source Address {columnSorting ? columnSorting("source_address") : null}</th>
      <th>Destination MAC {columnSorting ? columnSorting("destination_mac") : null}</th>
      <th>Destination Address {columnSorting ? columnSorting("destination_address") : null}</th>
      <th className="hide-narrow">TURN {columnSorting ? columnSorting("is_turn") : null}</th>
      <th>Tags</th>
      <th title="Mapped Addresses" className="hide-narrow">M</th>
      <th title="Peer Addresses" className="hide-narrow">P</th>
      <th title="Relayed Addresses" className="hide-narrow">R</th>
      <th>Bytes {columnSorting ? columnSorting("bytes") : null}</th>
      <th>Initiated At {columnSorting ? columnSorting("initiated_at") : null}</th>
      <th>Last Activity {columnSorting ? columnSorting("last_activity") : null}</th>
      <th>A {columnSorting ? columnSorting("is_active") : null}</th>
    </tr>
  )

}