import React, {useState} from "react";
import ContextOverlayVisibilityWrapper from "../ContextOverlayVisibilityWrapper";
import Dot11MacAddressType from "./Dot11MacAddressType";
import ApiRoutes from "../../../../util/ApiRoutes";
import AssetActiveIndicator from "../../../ethernet/assets/AssetActiveIndicator";

function MacAddress(props) {

  const addressWithContext = props.addressWithContext;
  const overlay = props.overlay ? props.overlay : null;
  const address = addressWithContext ? addressWithContext.address : props.address;
  const assetIsActive = addressWithContext ? addressWithContext.asset_is_active : null;
  const context = addressWithContext ? addressWithContext.context : null;
  const oui = addressWithContext ? addressWithContext.oui : null;
  const isRandomized = addressWithContext ? addressWithContext.is_randomized : (props.address ? props.address.is_randomized : null);

  const href = props.href;
  const withAssetLink = props.withAssetLink;
  const withAssetName = props.withAssetName;
  const onClick = props.onClick;
  const hideActiveIndicator = props.hideActiveIndicator;

  const type = props.type;

  const showOui = props.showOui;
  const highlighted = props.highlighted;
  const filterElement = props.filterElement;

  const [overlayTimeout, setOverlayTimeout] = useState(null);
  const [overlayVisible, setOverlayVisible] = useState(false);

  const addressElement = () => {
    let computedHref = href;
    if (withAssetLink && addressWithContext && addressWithContext.asset_id) {
      // Asset link requested.
      computedHref = ApiRoutes.ETHERNET.ASSETS.DETAILS(addressWithContext.asset_id);
    }

    if (computedHref || onClick) {
      return <a href={computedHref ? computedHref : "#"}
                onClick={onClick ? onClick : () => {}}
                className={highlighted ? "highlighted" : null}>{address}</a>
    } else {
      return <span style={{cursor: "help"}} className={highlighted ? "highlighted" : null}>{address}</span>;
    }
  }

  const assetNameElement = () => {
    if (withAssetName && context && context.name) {
      return <span className="context-name hide-narrow" style={{marginLeft: 5}}>{context.name}</span>;
    }

    return null;
  }

  const contextElement = () => {
    if (!context) {
      return null;
    }

    return <i className="fa-solid fa-circle-info additional-context-available"
              title="Additional context available." />
  }

  const ouiElement = () => {
    if (!oui || !showOui) {
      return null;
    }

    return ( <span className="mac-address-oui">(Vendor: {oui ? oui : "Unknown"})</span>)
  }

  const typeElement = () => {
    if (type) {
      return <Dot11MacAddressType type={type}/>
    }
  }

  const randomizedIcon = () => {
    if (!isRandomized) {
      return null;
    }

    return <i className="fa-solid fa-shuffle mac-address-randomized" title="Randomized MAC Address" />
  }

  const mouseOver = () => {
    if (!overlay) {
      return;
    }

    setOverlayVisible(false);
    setOverlayTimeout(setTimeout(() => setOverlayVisible(true), 1000));
  }

  const mouseOut = () => {
    if (!overlay) {
      return;
    }

    setOverlayVisible(false);
    if (overlayTimeout) {
      clearTimeout(overlayTimeout);
    }
  }

  if (!address && !addressWithContext) {
    return <span className="text-muted">n/a</span>;
  }

  return (
      <span onMouseEnter={mouseOver} onMouseLeave={mouseOut}>
        {hideActiveIndicator ? null : <AssetActiveIndicator active={assetIsActive} hideText={true} />}
        {typeElement()}{addressElement()} {randomizedIcon()}{contextElement()}{ouiElement()}{assetNameElement()}{filterElement ? filterElement : null}

        {overlay ? <ContextOverlayVisibilityWrapper visible={overlayVisible} overlay={overlay} /> : null }
      </span>
  )

}

export default MacAddress;