import React, {Suspense} from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";

const ConnectionGlobe = React.lazy(() => import("../../shared/maps/ConnectionGlobe"));

export default function TopologyMap({model, whole, selection, onSelect, focus, height, live = false}) {

  const info = (text) => (
      <div className="border rounded d-flex align-items-center justify-content-center text-muted text-center p-4"
           style={{height: height + "px"}}>
        <div style={{maxWidth: "36rem"}}>{text}</div>
      </div>
  );

  // A live map stays up while it is empty: the next second can bring traffic, and the globe is slow to set up.
  if (whole.external === 0 && !live) {
    return info("No external traffic matches the selected filters.");
  }

  if (whole.points.length === 0 && !live) {
    return info("No GeoIP data. Placing external addresses on the map needs their country, which Nzyme Connect provides.");
  }

  if (whole.origins.length === 0) {
    return info("None of the selected taps has a known position. Assign the taps to locations with coordinates, or give "
        + "the taps coordinates.");
  }

  const notes = [];
  if (model.unplacedTaps.length > 0 && model.unplaced.sessions > 0) {
    notes.push(formatBytes(model.unplaced.bytes) + " recorded by "
        + (model.unplacedTaps.length === 1 ? "the tap " : "the taps ")
        + model.unplacedTaps.map((t) => t.name).join(", ")
        + " has no flow on the map, because " + (model.unplacedTaps.length === 1 ? "this tap has" : "these taps have")
        + " no known position.");
  }
  if (model.unlocated.sessions > 0) {
    notes.push(formatCount(model.unlocated.sessions) + (model.unlocated.sessions === 1 ? " session" : " sessions")
        + " with external addresses without a known location " + (model.unlocated.sessions === 1 ? "is" : "are")
        + " not on the map.");
  }

  const selectedId = selection && (selection.kind === "place" || selection.kind === "site") ? selection.id : null;

  return (
      <React.Fragment>
        <Suspense fallback={
          <div className="border rounded" style={{height: height + "px"}}>
            <GenericWidgetLoadingSpinner height={height - 2} />
          </div>}>
          <ConnectionGlobe origins={model.origins}
                           points={model.points}
                           flows={model.flows}
                           selectedId={selectedId}
                           onSelect={(place) => onSelect(place ? {kind: "place", id: place.id} : null)}
                           onSelectOrigin={(site) => onSelect({kind: "site", id: site.id})}
                           focus={focus}
                           height={height} />
        </Suspense>
        {notes.map((note, i) => <div key={i} className="text-muted small mt-1">{note}</div>)}
      </React.Fragment>
  );

}
