import React from "react";
import {SEGMENT_KINDS, SELECTOR_TYPES, selectorLabel} from "../SegmentationFormat";
import {isEverythingInScope, isUnbounded, servicesText} from "./PolicyDefinition";
import PolicyAssetLabel from "./PolicyAssetLabel";

export function PolicySelector({selector, options, plain = false}) {

  switch (selector.type) {
    case "ASSET":
      return selector.value ? <PolicyAssetLabel uuid={selector.value} link={!plain} /> : selectorLabel(selector, options);
    case "TAG": {
      const tag = options && options.tags ? options.tags.find((t) => t.uuid === selector.value) : null;

      return (
        <span className={"text-nowrap" + (tag ? "" : " text-warning")}
              title={tag ? "Assets with the tag " + tag.name + (tag.description ? ": " + tag.description : "")
                : "The tag does not exist anymore."}>
          <i className={"fa-solid fa-tag" + (plain ? "" : " text-muted")}></i> {selectorLabel(selector, options)}
        </span>
      )
    }
    case "SEGMENT": {
      const segment = options && options.segments ? options.segments.find((s) => s.uuid === selector.value) : null;

      if (!segment) {
        return <span className="text-warning" title="The segment does not exist anymore.">{selectorLabel(selector, options)}</span>
      }

      return (
        <span title={SEGMENT_KINDS[segment.kind].title + (segment.description ? ": " + segment.description : "")}>
          {segment.name}
        </span>
      )
    }
    case "ADDRESS":
      return <span className={plain ? undefined : "machine-data"}>{selector.value}</span>
    default: {
      const type = SELECTOR_TYPES[selector.type];
      return <span title={type && type.description ? type.description : undefined}>{selectorLabel(selector, options)}</span>
    }
  }

}

export default function PolicyRuleText({rule, options, plain = false, scope = false}) {

  if (!scope && isEverythingInScope(rule)) {
    return <span>Everything in scope</span>
  }

  const selectors = (list, any) => {
    if (isUnbounded(list)) {
      return any;
    }

    return list.map((selector, i) => {
      return (
        <React.Fragment key={i}>
          {i > 0 ? <span className="text-muted"> or </span> : null}
          <PolicySelector selector={selector} options={options} plain={plain} />
        </React.Fragment>
      )
    });
  }

  return (
    <span>
      {selectors(rule.sources, scope ? "Anything" : "Anything in scope")}
      <span className="text-muted"> to </span>
      {selectors(rule.destinations, scope ? "any host" : "anything in scope")}
      <span className="text-muted"> on </span>
      {servicesText(rule.services)}
    </span>
  )

}
