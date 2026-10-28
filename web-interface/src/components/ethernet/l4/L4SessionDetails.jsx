import React from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import L4SessionState from "./L4SessionState";
import InternalAddressOnlyWrapper from "../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import L4SessionTags from "./L4SessionTags";
import L4Address from "../shared/L4Address";
import numeral from "numeral";
import moment from "moment/moment";
import {formatDurationMs} from "../../../util/Tools";
import L4TagDetails from "./L4TagDetails";

export default function L4SessionDetails({type, session}) {

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Overview" />

                <dl className="mb-0">
                  <dt>Source MAC</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                      address={session.source}
                      inner={<EthernetMacAddress
                          addressWithContext={session.source.mac}
                          assetId={session.source && session.source.asset_id ? session.source.asset_id : null}
                          withAssetLink withAssetName />} />
                  </dd>
                  <dt>Source Address</dt>
                  <dd><L4Address address={session.source} /></dd>
                  <dt>Destination MAC</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={session.destination}
                        inner={<EthernetMacAddress
                            addressWithContext={session.destination.mac}
                            withAssetLink withAssetName/>} />
                  </dd>
                  <dt>Destination Address</dt>
                  <dd><L4Address address={session.destination} /></dd>
                  <dt>State</dt>
                  <dd><L4SessionState state={session.state} showFull={true} /></dd>
                  <dt>Tags</dt>
                  <dd><L4SessionTags tags={session.tags} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Data" />

                <dl className="mb-0">
                  <dt>L4 Type</dt>
                  <dd>{type}</dd>
                  <dt>Total Traffic Exchanged</dt>
                  <dd>{numeral(session.bytes_count).format("0b")}</dd>
                  <dt>RX Traffic</dt>
                  <dd>{numeral(session.bytes_rx_count).format("0b")}</dd>
                  <dt>TX Traffic</dt>
                  <dd>{numeral(session.bytes_tx_count).format("0b")}</dd>
                  <dt>Segments</dt>
                  <dd>{numeral(session.segments_count).format("0,0")}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Timing" />

                <dl className="mb-0">
                  <dt>Start Time</dt>
                  <dd>{moment(session.start_time).format()} ({moment(session.start_time).fromNow()})</dd>
                  <dt>End Time</dt>
                  <dd>
                    {session.end_time ? moment(session.end_time).format() : <span className="text-muted">n/a</span>}
                    {session.end_time ? <span> ({moment(session.end_time).fromNow()})</span> : null}
                  </dd>
                  <dt>Duration</dt>
                  <dd>{formatDurationMs(session.duration_ms)}</dd>
                  <dt>Most Recent Segment Time</dt>
                  <dd>{moment(session.most_recent_segment_time).format()} ({moment(session.most_recent_segment_time).fromNow()})</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <L4TagDetails session={session} type={type} />

      </React.Fragment>
  )

}