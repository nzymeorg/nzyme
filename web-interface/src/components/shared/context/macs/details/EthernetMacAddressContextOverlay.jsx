import React, {useEffect, useState} from "react";
import WithPermission from "../../../../misc/WithPermission";
import ApiRoutes from "../../../../../util/ApiRoutes";
import ContextService from "../../../../../services/ContextService";
import {truncate} from "../../../../../util/Tools";
import FirstContextIpAddress from "./FirstContextIpAddress";
import FirstContextHostname from "./FirstContextHostname";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import ContextOverlayLoading from "../../ContextOverlayLoading";

const contextService = new ContextService();

export default function EthernetMacAddressContextOverlay(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const address = props.address;
  const oui = props.oui;

  // Optional.
  const assetId = props.assetId;

  const [ctx, setCtx] = useState(null);

  useEffect(() => {
    setCtx(null);
    contextService.findMacAddressContext(address, organizationId, tenantId, setCtx);
  }, [address]);

  const assetLink = () => {
    if (!assetId) {
      return null;
    }

    return (
        <React.Fragment>
          <a href={ApiRoutes.ETHERNET.ASSETS.DETAILS(assetId)}
             className="btn btn-sm btn-outline-primary">
            Open Asset Page
          </a>{' '}
        </React.Fragment>
    )
  }

  if (!ctx) {
    return <ContextOverlayLoading />
  }

  if (!ctx.context) {
    return (
        <React.Fragment>
          <h6><i className="fa-regular fa-address-card" /> {address}</h6>

          <div className="context-overlay-content">
            <p className="context-description">
              <i className="fa-solid fa-circle-info"></i> This MAC address has no context.
            </p>

            <dl>
              <dt>Device Type:</dt>
              <dd>Ethernet</dd>
              <dt>OUI:</dt>
              <dd>{oui ? truncate(oui, 20) : <span className="text-muted">Unknown</span>}</dd>
              <dt>Has Notes:</dt>
              <dd>No</dd>
            </dl>
          </div>

          <div className="context-overlay-actions">
            {assetLink()}

            <WithPermission permission="mac_context_manage">
              <a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.CREATE + "?address=" + encodeURIComponent(address)}
                 className="btn btn-sm btn-outline-secondary">
                Add Context
              </a>
            </WithPermission>
          </div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <h6>
          <i className="fa-regular fa-address-card" /> {address}{' '}
          <span className="context-name context-name-asset">{ctx.context.name}</span>
        </h6>

        <div className="context-overlay-content">
          <p className="context-description">
            <i className="fa-solid fa-angle-right"></i>{' '}
            {ctx.context.description && ctx.context.description.trim().length > 0
                ? ctx.context.description : "No Description"}
          </p>

          <dl>
            <dt>Device Type:</dt>
            <dd>Ethernet Device</dd>
            <dt>OUI:</dt>
            <dd>{oui ? truncate(oui, 20) : <span className="text-muted">Unknown</span>}</dd>
            <dt>IP Address</dt>
            <dd><FirstContextIpAddress addresses={ctx.context.transparent_ip_addresses}/></dd>
            <dt>Hostname</dt>
            <dd><FirstContextHostname hostnames={ctx.context.transparent_hostnames}/></dd>
            <dt>Has Notes:</dt>
            <dd>{ctx.context.notes ? "Yes" : "No"}</dd>
          </dl>
        </div>

        <div className="context-overlay-actions">
          {assetLink()}

          <a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(ctx.context.uuid)}
             className="btn btn-sm btn-outline-primary">
            Context Details
          </a>
        </div>
      </React.Fragment>
  )

}