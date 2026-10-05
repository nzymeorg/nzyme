import React, {useState} from "react";
import IPAddressLink from "./IPAddressLink";
import Flag from "../../misc/Flag";
import ContextOverlayVisibilityWrapper from "../../shared/context/ContextOverlayVisibilityWrapper";
import L4AddressContextOverlay from "../l4/ip/L4AddressContextOverlay";

export default function L4Address({address,
                                    hidePort = false,
                                    hideFlag = false,
                                    withAssetName = false,
                                    filterElement = undefined,
                                    suffixElement = undefined}) {

  const [overlayTimeout, setOverlayTimeout] = useState(null);
  const [overlayVisible, setOverlayVisible] = useState(false);

  const geoCountryCode = () => {
    if (!address.geo || !address.geo.country_code) {
      return "NONE"
    } else {
      return address.geo.country_code;
    }
  }

  const flag = () => {
    if (hideFlag) {
      return false;
    }

    return <Flag code={geoCountryCode() }/>
  }

  const assetNameElement = () => {
    if (withAssetName && address.attributes && address.attributes.is_site_local && address.mac && address.mac.context && address.mac.context.name) {
      return <span className="context-name hide-narrow context-name-asset" style={{marginLeft: 5}}>{address.mac.context.name}</span>;
    }

    return null;
  }

  const contextElement = () => {
    if (!address.context || !address.context.networks || address.context.networks.length === 0) {
      return null
    }

    return <i className="fa-solid fa-circle-info additional-context-available"
              title="Additional context available." />
  }

  const mouseOver = () => {
    setOverlayVisible(false);
    setOverlayTimeout(setTimeout(() => setOverlayVisible(true), 1000));
  }

  const mouseOut = () => {
    setOverlayVisible(false);
    if (overlayTimeout) {
      clearTimeout(overlayTimeout);
    }
  }

  if (!address) {
    return (
        <span>
          [missing] <i className="fa-solid fa-circle-question"
                       title="Underlying connection information has been retention cleaned or not recorded."></i>
        </span>
    )
  }

  return (
      <span onMouseEnter={mouseOver} onMouseLeave={mouseOut}>
        {flag()}{' '}
        <IPAddressLink ip={address.address} port={hidePort || !address.port ? null : address.port} />{' '}
        {contextElement()}{assetNameElement()}{' '}
        {filterElement ? filterElement : null}{' '}
        {suffixElement ? suffixElement : null}

        <ContextOverlayVisibilityWrapper visible={overlayVisible}
                                         overlay={<L4AddressContextOverlay address={address} />} />
      </span>
  )

}