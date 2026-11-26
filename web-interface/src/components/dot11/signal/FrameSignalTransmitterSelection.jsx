import React, {useContext, useEffect, useState} from "react";
import numeral from "numeral";
import moment from "moment";
import {TapContext} from "../../../App";
import Dot11Service from "../../../services/Dot11Service";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../misc/Paginator";
import AutomaticDot11MacAddressLink from "../../shared/context/macs/AutomaticDot11MacAddressLink";
import PivotLink from "../../shared/PivotLink";
import FrameSignalError from "./FrameSignalError";
import {useFrameSignalInvestigation} from "./FrameSignalInvestigation";

const dot11Service = new Dot11Service();

export default function FrameSignalTransmitterSelection({timeRange}) {

  const investigation = useFrameSignalInvestigation();

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const scope = JSON.stringify([organizationId, tenantId, timeRange, selectedTaps]);

  const [loaded, setLoaded] = useState(null);
  const [chosenPage, setChosenPage] = useState(null);

  const perPage = 10;
  const page = chosenPage && chosenPage.scope === scope ? chosenPage.page : 1;
  const setPage = (newPage) => setChosenPage({scope: scope, page: newPage});

  useEffect(() => {
    let current = true;
    dot11Service.findFrameSignalTransmitters(investigation.apiPath, organizationId, tenantId, timeRange, selectedTaps, perPage,
        (page - 1) * perPage,
        (data) => current && setLoaded({scope: scope, page: page, data: data}),
        (error) => current && setLoaded({scope: scope, page: page, error: error}));

    return () => {
      current = false;
    }
  }, [scope, page]);

  if (!loaded || loaded.scope !== scope || loaded.page !== page) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  if (loaded.error) {
    return <FrameSignalError error={loaded.error} />
  }

  const transmitters = loaded.data;

  if (transmitters.total === 0) {
    return <div className="alert alert-info mb-0">The selected taps recorded no {investigation.frames}.</div>
  }

  return (
      <React.Fragment>
        <p className="mb-2">
          <strong>Total:</strong> {numeral(transmitters.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>{investigation.transmitterTitle}</th>
            <th>{investigation.framesTitle}</th>
            {investigation.hasReceivers ? <th>Receivers</th> : null}
            <th>Frames to Compare With</th>
            <th>Last Frame</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {transmitters.transmitters.map((transmitter) => {
            const address = transmitter.mac.mac.address;

            return (
                <tr key={address}>
                  <td>
                    <AutomaticDot11MacAddressLink mac={address}
                                                  type={transmitter.mac.type}
                                                  addressWithContext={transmitter.mac.mac} />
                  </td>
                  <td>{numeral(transmitter.frame_count).format("0,0")}</td>
                  {investigation.hasReceivers
                      ? <td>{numeral(transmitter.receiver_count).format("0,0")}</td> : null}
                  <td>
                    {transmitter.comparison_frame_count === 0 ? <span className="text-muted">None</span>
                        : <React.Fragment>
                          {numeral(transmitter.comparison_frame_count).format("0,0")}{" "}
                          <span className="text-muted">
                            ({transmitter.type === "ACCESS_POINT" ? "beacons" : "other frames"})
                          </span>
                        </React.Fragment>}
                  </td>
                  <td title={moment(transmitter.last_seen).format()}>{moment(transmitter.last_seen).fromNow()}</td>
                  <td className="text-end">
                    <PivotLink to={investigation.link([address], timeRange)}>Investigate</PivotLink>
                  </td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={transmitters.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
