import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import ContextService from "../../../../services/ContextService";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ContextOverlayLoading from "../../../shared/context/ContextOverlayLoading";
import {hasSegmentContext, L4AddressSegmentDetails, segmentHasNotes} from "../../shared/L4AddressSegment";
import {macIsDevice} from "../../shared/L4AddressMacRole";
import {nat64EmbeddedAddress} from "../../../../util/Nat64";

const countries = require("i18n-iso-countries");
countries.registerLocale(require("i18n-iso-countries/langs/en.json"));

const contextService = new ContextService();

const octetsOf = (ip) => String(ip).includes(":") ? [] : String(ip).split(".").map(Number);

// 100.64.0.0/10 is site-local only where the TCP/UDP settings say so.
const isCgnat = (ip) => {
  const octets = octetsOf(ip);
  return octets.length === 4 && octets[0] === 100 && octets[1] >= 64 && octets[1] <= 127;
}

const isRfc1918 = (ip) => {
  const octets = octetsOf(ip);
  return octets.length === 4 && (octets[0] === 10
      || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
      || (octets[0] === 192 && octets[1] === 168));
}

const siteLocalSummary = (ip) => {
  if (isCgnat(ip)) {
    return "Internal / CGNAT IP Address";
  }

  return isRfc1918(ip) ? "Site-Local / RFC 1918 IP Address" : "Internal IP Address";
}

export default function L4AddressContextOverlay({address}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [ctx, setCtx] = useState(null);

  useEffect(() => {
    setCtx(null);
    contextService.findIpAddressContext(address.address, organizationId, tenantId, setCtx);
  }, [address]);

  const attributeSummary = () => {
    if (!address.attributes) {
      return null;
    }

    if (address.attributes.is_site_local) {
      return siteLocalSummary(address.address)
    }

    if (address.attributes.is_loopback) {
      return "Loopback IP Address"
    }

    if (address.attributes.is_multicast) {
      return "Multicast IP Address"
    }
  }

  const attributes = () => {
    if (!address.attributes) {
      return ["None"];
    }

    let attributes = [];

    if (address.attributes.is_site_local) {
      attributes.push(isCgnat(address.address) ? "Internal (CGNAT)" : isRfc1918(address.address) ? "Site-Local" : "Internal");
    }

    if (address.attributes.is_loopback) {
      attributes.push("Loopback");
    }

    if (address.attributes.is_multicast) {
      attributes.push("Multicast");
    }

    const nat64 = nat64EmbeddedAddress(address.address);
    if (nat64) {
      attributes.push("NAT64 for " + nat64);
    }

    if (attributes.length === 0) {
      return "None";
    } else {
      return attributes.join(", ")
    }
  }

  const associatedAsset = () => {
    if (!ctx.assets || ctx.assets.length === 0) {
      return <span className="text-muted">None</span>
    }

    const asset = ctx.assets[0]
    const more = ctx.assets.length-1;

    let name;
    if (asset.name && asset.name.trim().length > 0) {
      name = asset.name;
    } else {
      name = asset.uuid.substr(0, 6)
    }

    return (
      <span>
        <Link to={ApiRoutes.ETHERNET.ASSETS.DETAILS(asset.uuid)} className="machine-data">{name}</Link>{' '}
        {more > 0 ? <span className="italic text-muted">[+{more} more]</span> : null}
      </span>
    )
  }

  const asn = () => {
    if (!address.geo || !address.geo.asn_name) {
      return <span className="text-muted">n/a</span>
    }

    return address.geo.asn_name + " (" + address.geo.asn_number + ")";
  }

  const country = () => {
    if (!address.geo || !address.geo.country_code) {
      return <span className="text-muted">n/a</span>
    }

    return countries.getName(address.geo.country_code, "en", {select: "official"}) + " (" + address.geo.country_code +")";
  }

  const city = () => {
    if (!address.geo || !address.geo.city) {
      return <span className="text-muted">n/a</span>
    }

    return address.geo.city;
  }

  const hasNotes = () => {
    if (segmentHasNotes(address.context)) {
      return true;
    }

    if (!ctx || ctx.context.length === 0) {
      return false;
    }

    for (const c of ctx.context) {
      if (c.notes && c.notes.trim().length > 0) {
        return true;
      }
    }

    return false;
  }

  const description = () => {
    if (!ctx || ctx.context.length === 0) {
      return <span className="text-muted">No Description</span>;
    }

    for (const c of ctx.context) {
      if (c.description && c.description.trim().length > 0) {
        return (
          <>
            <span>
              {c.description}{' '}
              {ctx.context.length > 1 ? <span className="italic text-muted">[+{ctx.context.length-1} more]</span> : null}
            </span>
          </>
        )
      }
    }

    return <span className="text-muted">No Description</span>
  }

  const name = () => {
    if (!ctx || ctx.context.length === 0) {
      return <span className="text-muted">No Name</span>;
    }

    for (const c of ctx.context) {
      if (c.name && c.name.trim().length > 0) {
        return (
          <>
            <span>
              <span className="context-name context-name-network">{c.name}</span>{' '}
              {ctx.context.length > 1 ? <span className="italic text-muted">[+{ctx.context.length-1} more]</span> : null}
            </span>
          </>
        )
      }
    }

    return <span className="text-muted">No Name</span>
  }

  const contextDetailsLink = () => {
    if (!ctx || ctx.context.length === 0) {
      return null;
    }

    if (ctx.context.length === 1) {
      // Single CIDR matched and we can link directly.
      return (
        <Link to={ApiRoutes.CONTEXT.NETWORKS.SHOW(ctx.context[0].uuid)}
           className="btn btn-sm btn-outline-primary">
          Context Details
        </Link>
      )
    }

    // Multiple CIDRs matched, so let the user pick one.
    return (
      <div className="btn-group">
        <button className="btn btn-sm btn-outline-primary dropdown-toggle text-nowrap"
                type="button"
                data-bs-toggle="dropdown"
                aria-expanded="false">
          Context Details
        </button>
        <ul className="dropdown-menu">
          {ctx.context.map((c) => (
            <li key={c.uuid}>
              <Link className="dropdown-item"
                 to={ApiRoutes.CONTEXT.NETWORKS.SHOW(c.uuid)}>
                {c.cidr}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  const segmentRow = () => {
    if (!hasSegmentContext(address.context)) {
      return null;
    }

    return (
      <React.Fragment>
        <dt>Segment:</dt>
        <dd><L4AddressSegmentDetails context={address.context} /></dd>
      </React.Fragment>
    )
  }

  if (!ctx) {
    return <ContextOverlayLoading />
  }

  if (address.attributes) {
    // This is a GEO-enriched address.
    if (address.attributes.is_site_local || address.attributes.is_multicast || address.attributes.is_loopback) {
      // Local IP.
      return (
          <React.Fragment>
            <h6>
              <i className="fa-solid fa-map-location-dot"/> <span className="context-address">{address.address}</span>{' '}
              <span className="context-name context-name-asset">
                {macIsDevice(address)
                  && address.mac && address.mac.context && address.mac.context.name ? address.mac.context.name : null}
              </span>
            </h6>

            <div className="context-overlay-content">
              <p className="context-description">
                <i className="fa-solid fa-circle-info"></i> {attributeSummary()}

                <div className="mt-2">
                  <i className="fa-solid fa-angle-right"></i> {description()}
                </div>
              </p>

              <dl className="ip-address">
                <dt>Network Name:</dt>
                <dd>{name()}</dd>
                {segmentRow()}
                <dt>Attributes:</dt>
                <dd>{attributes()}</dd>
                <dt>Associated Asset:</dt>
                <dd>{associatedAsset()}</dd>
                <dt>Has Notes:</dt>
                <dd>{hasNotes() ? <span className="bold text-warning">Yes</span> : "No"}</dd>
              </dl>
            </div>

            <div className="context-overlay-actions d-flex flex-nowrap align-items-center gap-1">
              <Link to={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)}
                 className="btn btn-sm btn-outline-primary text-nowrap">
                Address Details
              </Link>
              {contextDetailsLink()}
            </div>
          </React.Fragment>
      )
    } else {
      // Not a local IP.
      return (
          <React.Fragment>
            <h6><i className="fa-solid fa-map-location-dot"/> <span className="context-address">{address.address}</span></h6>

            <div className="context-overlay-content">
              <p className="context-description">
                <i className="fa-solid fa-angle-right"></i> {description()}
              </p>

              <dl className="ip-address">
                <dt>Network Name:</dt>
                <dd>{name()}</dd>
                {segmentRow()}
                <dt>Attributes:</dt>
                <dd>{attributes()}</dd>
                <dt>ASN:</dt>
                <dd>{asn()}</dd>
                <dt>ASN Domain:</dt>
                <dd>{address.geo && address.geo.asn_domain ? address.geo.asn_domain :
                    <span className="text-muted">n/a</span>}</dd>
                <dt>Country:</dt>
                <dd>{country()}</dd>
                <dt>City:</dt>
                <dd>{city()}</dd>
                <dt>Has Notes:</dt>
                <dd>{hasNotes() ? <span className="bold text-warning">Yes</span> : "No"}</dd>
              </dl>
            </div>

            <div className="context-overlay-actions d-flex flex-nowrap align-items-center gap-1">
              <Link to={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)}
                 className="btn btn-sm btn-outline-primary text-nowrap">
                Address Details
              </Link>
              {contextDetailsLink()}
            </div>
          </React.Fragment>
      )
    }
  } else {
    // Not a GEO-enriched address and no attributes.

    return (
        <React.Fragment>
          <h6><i className="fa-solid fa-map-location-dot"/> <span className="context-address">{address.address}</span></h6>

          <div className="context-overlay-content">
            <p className="context-description">
              No attributes.

              <div className="mt-2">
                <i className="fa-solid fa-angle-right"></i> {description()}
              </div>
            </p>

            <dl className="ip-address">
              <dt>Network Name:</dt>
              <dd>{name()}</dd>
              {segmentRow()}
              <dt>Attributes:</dt>
              <dd>{attributes()}</dd>
              <dt>Has Notes:</dt>
              <dd>{hasNotes() ? <span className="bold text-warning">Yes</span> : "No"}</dd>
            </dl>
          </div>

          <div className="context-overlay-actions d-flex flex-nowrap align-items-center gap-1">
            <Link to={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)}
               className="btn btn-sm btn-outline-primary text-nowrap">
              Address Details
            </Link>
            {contextDetailsLink()}
          </div>
        </React.Fragment>
    )
  }

}