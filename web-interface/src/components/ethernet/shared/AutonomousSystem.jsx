import React, {useEffect, useState} from "react";
import ContextOverlayVisibilityWrapper from "../../shared/context/ContextOverlayVisibilityWrapper";
import AutonomousSystemContextOverlay from "./AutonomousSystemContextOverlay";

export default function AutonomousSystem({number,
                                           name = null,
                                           domain = null,
                                           countryCode = null,
                                           network = null,
                                           filterElement = undefined,
                                           children = undefined}) {

  const [overlayTimeout, setOverlayTimeout] = useState(null);
  const [overlayVisible, setOverlayVisible] = useState(false);

  useEffect(() => {
    return () => {
      if (overlayTimeout) {
        clearTimeout(overlayTimeout);
      }
    };
  }, [overlayTimeout]);

  const mouseOver = () => {
    setOverlayVisible(false);
    setOverlayTimeout(setTimeout(() => setOverlayVisible(true), 1000));
  }

  const mouseOut = () => {
    setOverlayVisible(false);
    setOverlayTimeout(null);
  }

  return (
      <span className="context-overlay-anchor" onMouseEnter={mouseOver} onMouseLeave={mouseOut}>
        {children ? children : <span className="asn-number context-overlay-hint">AS{number}</span>}{' '}
        {filterElement ? filterElement : null}

        <ContextOverlayVisibilityWrapper visible={overlayVisible}
                                         fit={true}
                                         overlay={<AutonomousSystemContextOverlay number={number}
                                                                                  name={name}
                                                                                  domain={domain}
                                                                                  countryCode={countryCode}
                                                                                  network={network} />} />
      </span>
  )

}
