import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";
import {SEGMENT_KINDS} from "../segmentation/SegmentationFormat";

const kindTitle = (segment) => SEGMENT_KINDS[segment.kind].title;

// Never more than two names, so that the row of the overlay stays at two lines.
const segmentByTapNames = (byTap) => {
  const names = byTap.segments.map((segment) => segment.name);
  const possibilities = names.length + (byTap.unassigned ? 1 : 0);

  if (possibilities <= 2) {
    return names.concat(byTap.unassigned ? ["none"] : []).join(" or ");
  }

  return names[0] + " or " + (possibilities - 1) + " more";
}

const segmentByTapNote = (byTap) => {
  return "Segment depends on the recording tap: " + byTap.segments.map((segment) => segment.name).join(", ")
    + (byTap.unassigned ? " or none" : "") + ".";
}

export function L4AddressSegmentLabel({context}) {
  if (!context) {
    return null;
  }

  if (context.segment) {
    return (
      <span className="context-name hide-narrow context-name-segment" style={{marginLeft: 5}}
            title={kindTitle(context.segment) + (context.segment.description ? ": " + context.segment.description : "")}>
        {context.segment.name}
      </span>
    )
  }

  if (context.segment_by_tap) {
    return <i className="fa-solid fa-code-branch additional-context-available hide-narrow"
              title={segmentByTapNote(context.segment_by_tap)} />
  }

  return null;
}

export function L4AddressSegmentDetails({context}) {
  if (!context) {
    return null;
  }

  if (context.segment) {
    return (
      <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.DETAILS(context.segment.uuid)}
            className="context-name context-name-segment"
            title={kindTitle(context.segment) + (context.segment.description ? ": " + context.segment.description : "")}>
        {context.segment.name}
      </Link>
    )
  }

  if (context.segment_by_tap) {
    return (
      <span title={segmentByTapNote(context.segment_by_tap)}>
        <i className="fa-solid fa-code-branch"></i> {segmentByTapNames(context.segment_by_tap)}{' '}
        <span className="text-muted text-nowrap">(by tap)</span>
      </span>
    )
  }

  return null;
}

export const hasSegmentContext = (context) => Boolean(context && (context.segment || context.segment_by_tap));

export const segmentHasNotes = (context) => {
  if (!context) {
    return false;
  }

  if (context.segment) {
    return Boolean(context.segment.has_notes);
  }

  return Boolean(context.segment_by_tap && context.segment_by_tap.segments.some((segment) => segment.has_notes));
}
