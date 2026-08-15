import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../../App";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import NATService from "../../../../../services/ethernet/NATService";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import L4Address from "../../../shared/L4Address";
import {capitalizeFirstLetterAndLowercase} from "../../../../../util/Tools";

const natService = new NATService();

// TODO: This will need to decide if something is a STUN discovery, TURN, or a full STUN session. For now its all just discovery.

export default function STUNSessionDetails({sessionId}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [session, setSession] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setSession(null);

    natService.findOneSTUNDiscovery(sessionId, organizationId, tenantId, selectedTaps, setSession)
  }, [sessionId, organizationId, tenantId, selectedTaps])

  const mappedAddresses = () => {
    if (session.mapped_addresses === null || session.mapped_addresses.length === 0) {
      return (
        <ul className="mb-0">
          <li>None</li>
        </ul>
      )
    }

    return (
      <ol className="mb-0">
        {session.mapped_addresses.map((a, i) => {
          return (
            <li key={i}>
              <L4Address address={a} />
            </li>
          )
        })}
      </ol>
    )
  }

  if (session == null) {
    return <LoadingSpinner />
  }

  return (
    <>
      <div className="row mt-3">
        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Status</dt>
                <dd>{capitalizeFirstLetterAndLowercase(session.status)}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Mapped Addresses" />

              {mappedAddresses()}
            </div>
          </div>
        </div>
      </div>
    </>
  )

}