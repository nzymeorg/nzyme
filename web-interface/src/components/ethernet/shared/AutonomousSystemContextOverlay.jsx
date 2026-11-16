import React from "react";
import {countryName} from "../assets/details/AssetProfileHelpers";

const NOT_AVAILABLE = <span className="text-muted">n/a</span>;

export default function AutonomousSystemContextOverlay({number, name, domain, countryCode, network}) {

  const country = () => {
    if (!countryCode) {
      return NOT_AVAILABLE;
    }

    const name = countryName(countryCode);
    return name === countryCode ? countryCode : name + " (" + countryCode + ")";
  }

  return (
      <React.Fragment>
        <h6>
          <i className="fa-solid fa-network-wired" /> AS{number}{' '}
          {network ? <span className="context-name context-name-network">{network}</span> : null}
        </h6>

        <div className="context-overlay-content">
          <p className="context-description">
            <i className="fa-solid fa-angle-right"></i>{' '}
            {name ? name : <span className="text-muted">No Name</span>}
          </p>

          <dl className="ip-address">
            <dt>ASN Name:</dt>
            <dd>{name ? name : NOT_AVAILABLE}</dd>
            <dt>ASN Domain:</dt>
            <dd>{domain ? domain : NOT_AVAILABLE}</dd>
            <dt>Country:</dt>
            <dd>{country()}</dd>
            <dt>External Network:</dt>
            <dd>{network ? network : <span className="text-muted">None</span>}</dd>
          </dl>
        </div>
      </React.Fragment>
  )

}
