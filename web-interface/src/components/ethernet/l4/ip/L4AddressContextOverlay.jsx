import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import ContextService from "../../../../services/ContextService";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ContextOverlayLoading from "../../../shared/context/ContextOverlayLoading";

const countries = require("i18n-iso-countries");
countries.registerLocale(require("i18n-iso-countries/langs/en.json"));

const contextService = new ContextService();

export default function L4AddressContextOverlay({address}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [ctx, setCtx] = useState(null);

  useEffect(() => {
    setCtx(null);
    contextService.findIpAddressContext(address.address, organizationId, tenantId, setCtx);
  }, [address]);

  const attributeSummary = () => {
    if (address.attributes === null) {
      return null;
    }

    if (address.attributes.is_site_local) {
      return "Site-Local / RFC 1918 IP Address"
    }

    if (address.attributes.is_loopback) {
      return "Loopback IP Address"
    }

    if (address.attributes.is_multicast) {
      return "Multicast IP Address"
    }
  }

  const attributes = () => {
    if (address.attributes === null) {
      return ["None"];
    }

    let attributes = [];

    if (address.attributes.is_site_local) {
      attributes.push("Site-Local");
    }

    if (address.attributes.is_loopback) {
      attributes.push("Loopback");
    }

    if (address.attributes.is_multicast) {
      attributes.push("Multicast");
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

    return (
      <span>
        <a href={ApiRoutes.ETHERNET.ASSETS.DETAILS(asset.uuid)} className="machine-data">{asset.name}</a>{' '}
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
              <i className="fa-solid fa-map-location-dot"/> {address.address}{' '}
              <span className="context-name">
                {address.attributes && address.attributes.is_site_local
                  && address.mac && address.mac.context && address.mac.context.name ? address.mac.context.name : null}
              </span>
            </h6>

            <div className="context-overlay-content">
              <p className="context-description">
                <i className="fa-solid fa-circle-info"></i> {attributeSummary()}
              </p>

              <dl className="ip-address">
                <dt>Attributes:</dt>
                <dd>{attributes()}</dd>
                <dt>Associated Asset:</dt>
                <dd>{associatedAsset()}</dd>
              </dl>
            </div>

            <div className="context-overlay-actions">
              <a href={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)} className="btn btn-sm btn-outline-primary">
                Open Address Details
              </a>
            </div>
          </React.Fragment>
      )
    } else {
      // Not a local IP.
      return (
          <React.Fragment>
            <h6><i className="fa-solid fa-map-location-dot"/> {address.address}</h6>

            <div className="context-overlay-content">
              <dl>
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
              </dl>
            </div>

            <div className="context-overlay-actions">
              <a href={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)} className="btn btn-sm btn-outline-primary">
                Open Address Details
              </a>
            </div>
          </React.Fragment>
      )
    }
  } else {
    // Not a GEO-enriched address and no attributes.

    return (
        <React.Fragment>
          <h6><i className="fa-solid fa-map-location-dot"/> {address.address}</h6>

          <div className="context-overlay-content">
            <p className="context-description">No attributes.</p>

            <dl>
              <dt>Attributes:</dt>
              <dd>{attributes()}</dd>
            </dl>
          </div>

          <div className="context-overlay-actions">
            <a href={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(address.address)} className="btn btn-sm btn-outline-primary">
              Open Address Details
            </a>
          </div>
        </React.Fragment>
    )
  }

}