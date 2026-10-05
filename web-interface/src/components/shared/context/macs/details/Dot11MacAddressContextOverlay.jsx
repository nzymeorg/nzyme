import React, {useEffect, useState} from "react";
import ContextService from "../../../../../services/ContextService";
import ApiRoutes from "../../../../../util/ApiRoutes";
import WithPermission from "../../../../misc/WithPermission";
import AssetImage from "../../../../misc/AssetImage";
import {truncate} from "../../../../../util/Tools";
import FirstContextIpAddress from "./FirstContextIpAddress";
import FirstContextHostname from "./FirstContextHostname";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";

const contextService = new ContextService();

function Dot11MacAddressContextOverlay(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const address = props.address;
  const oui = props.oui;
  const isRandomized = props.isRandomized;

  const [ctx, setCtx] = useState(null);

  useEffect(() => {
    setCtx(null);
    contextService.findMacAddressContext(address, organizationId, tenantId, setCtx);
  }, [address]);

  const contextType = (type) => {
    switch (type) {
      case "DOT11_CLIENT":
        return <span>802.11/WiFi Client</span>
      case "DOT11_AP":
        return <span>802.11/WiFi Access Point</span>
      case "DOT11_MIXED":
        return <span>802.11/WiFi Mixed/Inconclusive</span>
      case "UNKNOWN":
      default:
        return <span>Unknown</span>
    }
  }

  const monitored = (type, network) => {
    if (type === "DOT11_AP") {
      if (network) {
        return (
            <React.Fragment>
              Yes ({truncate(network.name, 22, false)}){' '}

              <WithPermission permission="dot11_monitoring_manage">
                <a href={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(network.uuid)} title="Open Monitored Network Details">
                  <i className="fa-solid fa-link"></i>
                </a>
              </WithPermission>
            </React.Fragment>
        )
      } else {
        return "No"
      }
    } else {
      return "n/a (Type not monitored)"
    }
  }

  const typeDetailsLink = (type, mac) => {
    let href;
    switch (type) {
      case "DOT11_CLIENT":
        href = ApiRoutes.DOT11.CLIENTS.DETAILS(mac);
        break;
      case "DOT11_AP":
        href = ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(mac);
        break;
      default:
        return null;
    }

    return <a href={href} className="btn btn-sm btn-outline-secondary">Device Details</a>
  }

  if (!ctx) {
    return (
        <React.Fragment>
          <AssetImage filename="loading-miller-notext.png"
                      className="loading-miller"
                      alt="loading ..." />

          <AssetImage filename="loading-miller_layer2-notext.png"
                      className="loading-miller loading-miller-layer2"
                      alt="loading ..." />
        </React.Fragment>
    )
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
              <dd>{contextType(ctx.context_type)}</dd>
              <dt>OUI:</dt>
              <dd>{oui ? truncate(oui, 20) : <span className="text-muted">Unknown</span>}</dd>
              <dt>Is Monitored:</dt>
              <dd>{monitored(ctx.context_type, ctx.serves_dot11_monitored_network)}</dd>
              <dt>IP Address</dt>
              <dd><span className="text-muted">None</span></dd>
              <dt>Hostname</dt>
              <dd><span className="text-muted">None</span></dd>
              <dt>Has Notes:</dt>
              <dd><span className="text-muted">No</span></dd>
              <dt>Is Randomized:</dt>
              <dd>{isRandomized === null ? <span className="text-muted">n/a</span> : (isRandomized ? "Yes" : "No")}</dd>
            </dl>
          </div>

          <div className="context-overlay-actions">
            <WithPermission permission="mac_context_manage">
              <a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.CREATE + "?address=" + encodeURIComponent(address)}
                 className="btn btn-sm btn-outline-primary">
                Add Context
              </a>{' '}
            </WithPermission>
            {typeDetailsLink(ctx.context_type, address)}
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
            <dd>{contextType(ctx.context_type)}</dd>
            <dt>OUI:</dt>
            <dd>{oui ? truncate(oui, 20) : <span className="text-muted">Unknown</span>}</dd>
            <dt>IP Address</dt>
            <dd><FirstContextIpAddress addresses={ctx.context.transparent_ip_addresses}/></dd>
            <dt>Hostname</dt>
            <dd><FirstContextHostname hostnames={ctx.context.transparent_hostnames}/></dd>
            <dt>Is Monitored:</dt>
            <dd>{monitored(ctx.context_type, ctx.serves_dot11_monitored_network)}</dd>
            <dt>Has Notes:</dt>
            <dd>{ctx.context.notes ? <span className="bold text-warning">Yes</span> : "No"}</dd>
            <dt>Is Randomized:</dt>
            <dd>{isRandomized === null ? <span className="text-muted">n/a</span> : (isRandomized ? "Yes" : "No")}</dd>
          </dl>
        </div>

        <div className="context-overlay-actions">
          <a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(ctx.context.uuid)}
             className="btn btn-sm btn-outline-primary">
            Context Details
          </a>{' '}
          {typeDetailsLink(ctx.context_type, address)}{' '}
        </div>
      </React.Fragment>
  )

}

export default Dot11MacAddressContextOverlay;