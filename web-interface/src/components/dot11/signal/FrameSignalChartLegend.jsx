import React from "react";
import {
  frameSignalMismatchColor,
  frameSignalReferenceColor,
  frameSignalReferenceDash,
  frameSignalSourceColor
} from "./FrameSignalChart";
import {useFrameSignalInvestigation} from "./FrameSignalInvestigation";

const item = {display: "inline-flex", alignItems: "center", marginRight: 20, whiteSpace: "nowrap"};

export default function FrameSignalChartLegend({sources}) {

  const investigation = useFrameSignalInvestigation();

  const reference = frameSignalReferenceColor();

  return (
      <div className="text-muted small mt-2">
        {sources.map((source, i) => {
          return (
              <React.Fragment key={source.address}>
                <span style={item}>
                  <span style={{width: 22, marginRight: 6, opacity: 0.6,
                    borderTop: "2px " + (frameSignalReferenceDash(i) === "solid" ? "solid " : "dashed ") + reference}} />
                  Average of the {source.type === "ACCESS_POINT" ? "beacons" : "other frames"} of {source.address}
                </span>
                <span style={item}>
                  <span style={{width: 10, height: 10, borderRadius: 5, marginRight: 6,
                    backgroundColor: frameSignalSourceColor(i)}} />
                  {investigation.framesLabel} of {source.address}
                </span>
              </React.Fragment>
          )
        })}
        <span style={item}>
          <span style={{width: 22, height: 10, marginRight: 6, opacity: 0.15, backgroundColor: reference}} />
          Range
        </span>
        <span style={item}>
          <span style={{width: 9, height: 9, marginRight: 7, transform: "rotate(45deg)",
            backgroundColor: frameSignalMismatchColor()}} />
          Mismatch
        </span>
      </div>
  )

}
