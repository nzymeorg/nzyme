import React, {useContext, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import TopologyGraph from "../../../ethernet/topology/TopologyGraph";
import useTopologyLive from "../../../ethernet/topology/useTopologyLive";
import {buildLinkModel} from "../../../ethernet/topology/TopologyData";
import {LIVE_WINDOW_MS} from "../../../ethernet/topology/TopologyLive";
import {formatBytes, formatCount} from "../../../ethernet/assets/details/AssetProfileHelpers";
import DashboardSources from "../DashboardSources";
import {SAMPLE_TOPOLOGY} from "./SampleData";

const FONT_FAMILY = "'Geist Mono', monospace";
// The graph is drawn in units of this many em, which puts its labels at the size of the small print of a panel.
const UNIT_EM = 1/11.5;
const DEFAULT_CONNECTIONS = 100;
// Names of taps and countries stay on displays that hide addresses and names.
const REDACTED_LABEL_LEVELS = ["TAP", "COUNTRY"];

const readStage = (element, previous) => {
  const style = window.getComputedStyle(element);
  const color = (name) => style.getPropertyValue(name).trim();
  const fontSize = parseFloat(style.fontSize);

  const colors = {
    background: color("--db-panel"),
    grid: color("--db-panel"),
    text: color("--db-text"),
    muted: color("--db-dim"),
    lines: color("--db-rule"),
    root: color("--db-text"),
    internal: color("--db-text"),
    internalLink: color("--db-dim"),
    external: color("--db-data"),
    outbound: color("--db-data"),
    inbound: color("--db-flow-in"),
    both: color("--db-flow-in"),
    selection: color("--db-text"),
    glow: parseFloat(color("--db-glow")) || 0
  };

  const signature = fontSize + "|" + Object.values(colors).join("|");
  if (!fontSize || (previous && previous.signature === signature)) {
    return previous;
  }

  return {signature: signature, colors: colors, fontFamily: FONT_FAMILY, unit: fontSize*UNIT_EM};
}

export default function TopologyWidget({widget, onMeta}) {

  const sources = useContext(DashboardSources);
  const live = Boolean(sources && sources.topology);

  const element = useRef(null);
  const [stage, setStage] = useState(null);
  const [available, setAvailable] = useState(true);
  const [redacted, setRedacted] = useState(false);

  const limit = parseInt(widget.options.connections, 10) || DEFAULT_CONNECTIONS;
  const key = live ? JSON.stringify([sources.key, widget.id, widget.options]) : null;

  // Colors and sizes follow the theme and the size of the screen, which both change without this widget knowing.
  useLayoutEffect(() => {
    if (element.current) {
      setStage((previous) => readStage(element.current, previous));
    }
  });

  useEffect(() => {
    setAvailable(true);
  }, [key]);

  const frame = useTopologyLive(key, live, (seconds, setData, onError) => {
    sources.topology(widget.id, (response) => {
      // Without a feed, the next request is made when this one would have timed out.
      setAvailable(Boolean(response.feed));
      setRedacted(Boolean(response.redacted));
      if (response.feed) {
        setData(response.feed, response.age_ms);
      }
    }, onError);
  }, {limit: limit, geo: false, flowLimit: 0});

  const links = live ? (frame ? frame.links : null) : SAMPLE_TOPOLOGY;

  const model = useMemo(() => {
    if (!links) {
      return null;
    }

    const built = buildLinkModel(links, null);
    if (redacted) {
      built.nodes.forEach((node) => {
        node.name = null;
        node.detail = null;
        if (!REDACTED_LABEL_LEVELS.includes(node.level)) {
          node.label = null;
        }
      });
    }

    return built;
  }, [links, redacted]);

  const shown = links ? links.links.length : 0;
  const total = links ? links.total_links : 0;

  let meta = null;
  let warn = false;
  if (live && frame && frame.stale) {
    meta = "No new data";
    warn = true;
  } else if (live && frame && frame.limited) {
    meta = "Most recent " + formatCount(frame.sessionLimit) + " sessions";
  } else if (links) {
    meta = (shown < total ? formatCount(shown) + " of " : "") + formatCount(total)
      + (total === 1 ? " connection" : " connections");
  }

  useEffect(() => {
    onMeta(meta === null ? null : {text: meta, warn: warn});
  }, [meta, warn]);

  const summary = live ? (frame ? frame.summary : null)
    : {internal: 6, external: 6, sessions: SAMPLE_TOPOLOGY.total_sessions, bytes: SAMPLE_TOPOLOGY.total_bytes};

  const note = !available ? "Not available" : (!model ? "Loading" : (model.nodes.size === 0 ? "No connections" : null));

  return (
    <div className="dashboard-topology" ref={element}>
      <div className="dashboard-topology-graph">
        {model && stage && available ?
          <TopologyGraph model={model}
                         scale={frame ? frame.scale : null}
                         dataKey={live ? key : "sample"}
                         labels="auto"
                         live={live}
                         pulses={frame ? frame.pulses : null}
                         stage={stage} /> : null}
        {note ? <div className="dashboard-widget-note dashboard-topology-note">{note}</div> : null}
      </div>

      <div className="dashboard-topology-foot">
        <div className="dashboard-topology-legend">
          <span><i className="dashboard-topology-internal"></i>Internal</span>
          <span><i className="dashboard-topology-external"></i>External</span>
          <span><i className="dashboard-topology-line dashboard-topology-out"></i>Outbound</span>
          <span><i className="dashboard-topology-line dashboard-topology-in"></i>Inbound</span>
          <span><i className="dashboard-topology-line dashboard-topology-local"></i>Internal only</span>
        </div>

        {summary && available ?
          <div className="dashboard-topology-facts">
            <span>Internal<b>{formatCount(summary.internal)}</b></span>
            <span>External<b>{formatCount(summary.external)}</b></span>
            <span>Sessions<b>{formatCount(summary.sessions)}</b></span>
            <span>Last {LIVE_WINDOW_MS/1000} s<b>{formatBytes(summary.bytes)}</b></span>
          </div> : null}
      </div>
    </div>
  )

}
