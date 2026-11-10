import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {brushX, easeLinear, select} from "d3";
import moment from "moment";
import Store from "../../../util/Store";
import {colorsForMode, FONT_FAMILY, tooltipStyle} from "../../shared/viz/VizTheme";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";
import {formatWindowTime} from "./TopologyData";
import {formatDurationMs} from "../../../util/Tools";

const CHART_HEIGHT = 74;
const AXIS_HEIGHT = 18;
const PADDING = 8;
// Milliseconds per step.
const SPEEDS = [["2400", "0.25×"], ["1200", "0.5×"], ["600", "1×"], ["300", "2×"], ["150", "4×"], ["75", "8×"]];

const GLIDE_OVERLAP = 1.25;

const CLASSES = [
  ["internal", "Internal", (c) => c.internal],
  ["outbound", "Outbound", (c) => c.outbound],
  ["inbound", "Inbound", (c) => c.inbound],
  ["external", "External to external", (c) => c.muted]
];

export default function TopologyTimeScrubber({timeline, timeWindow, setTimeWindow, onApply}) {

  const containerRef = useRef(null);
  const brushGroupRef = useRef(null);
  const brushRef = useRef(null);
  const draggingRef = useRef(false);
  const frameRef = useRef(null);
  const windowRef = useRef(timeWindow);
  windowRef.current = timeWindow;
  const shownRef = useRef(null);

  const [width, setWidth] = useState(0);
  const [metric, setMetric] = useState("bytes");
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState("600");
  const [repeat, setRepeat] = useState(true);
  const [hovered, setHovered] = useState(null);

  const darkMode = Boolean(Store.get("dark_mode"));
  const colors = colorsForMode(darkMode);

  const interval = parseInt(speed, 10);
  const intervalRef = useRef(interval);
  intervalRef.current = interval;

  const size = timeline.bucket_size_ms;
  const count = timeline.buckets.length;
  const start = count > 0 ? moment(timeline.buckets[0].bucket).valueOf() : 0;
  const end = start + count * size;

  const innerWidth = Math.max(0, width - PADDING * 2);
  const barWidth = count > 0 ? innerWidth / count : 0;

  const xOf = useCallback((ms) => PADDING + (end > start ? (ms - start) / (end - start) : 0) * innerWidth,
      [start, end, innerWidth]);

  const bars = useMemo(() => {
    const totals = timeline.buckets.map((b) => CLASSES.map(([key]) => b[metric + "_" + key]));
    const max = totals.reduce((m, values) => Math.max(m, values.reduce((sum, v) => sum + v, 0)), 0);
    return {totals: totals, max: max};
  }, [timeline, metric]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    setWidth(container.clientWidth);

    // Applied in the next frame: a resize inside the observer callback is reported as a ResizeObserver loop.
    let frame = null;
    const observer = new window.ResizeObserver(() => {
      if (frame !== null) return;
      frame = window.requestAnimationFrame(() => {
        frame = null;
        setWidth(container.clientWidth);
      });
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      if (frame !== null) window.cancelAnimationFrame(frame);
    };
  }, []);

  const snap = useCallback((x0, x1) => {
    if (innerWidth <= 0 || count === 0) return null;
    const indexOf = (x) => (x - PADDING) / innerWidth * count;
    const first = Math.max(0, Math.min(count - 1, Math.round(indexOf(x0))));
    const last = Math.max(first + 1, Math.min(count, Math.round(indexOf(x1))));
    return {from: start + first * size, to: start + last * size};
  }, [innerWidth, count, start, size]);

  useEffect(() => {
    const group = brushGroupRef.current;
    if (!group || innerWidth <= 0 || count === 0) return undefined;

    const brush = brushX()
        .extent([[PADDING, 0], [PADDING + innerWidth, CHART_HEIGHT]])
        .on("start", (event) => {
          if (!event.sourceEvent) return;
          draggingRef.current = true;
          setPlaying(false);
        })
        .on("brush", (event) => {
          // Moves made from code, to follow the window, have no source event.
          if (!event.sourceEvent || !event.selection) return;
          const next = snap(event.selection[0], event.selection[1]);
          if (frameRef.current !== null) window.cancelAnimationFrame(frameRef.current);
          frameRef.current = window.requestAnimationFrame(() => {
            frameRef.current = null;
            const current = windowRef.current;
            if (next && (!current || current.from !== next.from || current.to !== next.to)) setTimeWindow(next);
          });
        })
        .on("end", (event) => {
          if (!event.sourceEvent) return;
          draggingRef.current = false;
          if (frameRef.current !== null) {
            window.cancelAnimationFrame(frameRef.current);
            frameRef.current = null;
          }

          setPlaying(false);
          // A click outside the window clears it.
          const next = event.selection ? snap(event.selection[0], event.selection[1]) : null;
          setTimeWindow(next);
          select(group).call(brush.move, next ? [xOf(next.from), xOf(next.to)] : null);
        });

    const selection = select(group);
    selection.call(brush);
    selection.select(".selection")
        .attr("fill", colors.selection)
        .attr("fill-opacity", 0.1)
        .attr("stroke", colors.selection)
        .attr("stroke-opacity", 0.75)
        .attr("rx", 2);
    brushRef.current = brush;

    const current = windowRef.current;
    selection.call(brush.move, current ? [xOf(current.from), xOf(current.to)] : null);

    return () => {
      selection.interrupt();
      selection.on(".brush", null);
      brushRef.current = null;
      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, [innerWidth, count, snap, xOf, setTimeWindow, colors.selection]);

  useEffect(() => {
    const group = brushGroupRef.current;
    if (!group || !brushRef.current || draggingRef.current) return;

    const previous = shownRef.current;
    shownRef.current = timeWindow;
    const target = timeWindow ? [xOf(timeWindow.from), xOf(timeWindow.to)] : null;
    const selection = select(group);

    const glides = playing && previous && timeWindow
        && timeWindow.from - previous.from === size && timeWindow.to - previous.to === size;
    if (glides) {
      // The glide outlasts the step and is taken over by the next one, so a late step does not stall the window.
      const lead = Math.min((GLIDE_OVERLAP - 0.5) * size, end - timeWindow.to);
      selection.transition().duration(intervalRef.current * GLIDE_OVERLAP).ease(easeLinear)
          .call(brushRef.current.move, [xOf(timeWindow.from + lead), xOf(timeWindow.to + lead)]);
    } else {
      selection.interrupt();
      selection.call(brushRef.current.move, target);
    }
  }, [timeWindow, xOf, playing, size, end]);

  const step = useCallback((direction) => {
    const current = windowRef.current;
    if (!current) {
      const span = Math.max(1, Math.round(count / 12)) * size;
      setTimeWindow({from: start, to: start + span});
      return true;
    }

    const span = current.to - current.from;
    const from = Math.max(start, Math.min(end - span, current.from + direction * size));
    if (from === current.from) {
      return false;
    }
    setTimeWindow({from: from, to: from + span});
    return true;
  }, [count, size, start, end, setTimeWindow]);

  useEffect(() => {
    if (!playing) return undefined;

    const timer = window.setInterval(() => {
      if (step(1)) return;

      // The window is at the end. A window that covers the whole time range has nowhere to start over from.
      const current = windowRef.current;
      const span = current ? current.to - current.from : 0;
      if (repeat && current && span < end - start) {
        setTimeWindow({from: start, to: start + span});
      } else {
        setPlaying(false);
      }
    }, interval);

    return () => window.clearInterval(timer);
  }, [playing, interval, repeat, step, start, end, setTimeWindow]);

  useEffect(() => {
    setPlaying(false);
  }, [timeline]);

  const togglePlay = () => {
    if (playing) {
      setPlaying(false);
      return;
    }

    const current = windowRef.current;
    if (current && current.to >= end) {
      setTimeWindow({from: start, to: start + (current.to - current.from)});
    } else if (!current) {
      step(1);
    }
    setPlaying(true);
  }

  const onPointerMove = (event) => {
    if (barWidth <= 0) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const index = Math.floor((x - PADDING) / barWidth);
    setHovered(index >= 0 && index < count && y <= CHART_HEIGHT ? {index: index, x: x} : null);
  }

  const ticks = useMemo(() => {
    if (count === 0 || innerWidth <= 0) return [];
    const wanted = Math.max(2, Math.min(8, Math.floor(innerWidth / 150)));
    const every = Math.max(1, Math.round(count / wanted));
    const day = 24 * 3600 * 1000;
    let format = "HH:mm";
    if (size >= day) {
      format = end - start > 300 * day ? "MMM D, YYYY" : "MMM D";
    } else if (end - start > 36 * 3600 * 1000) {
      format = "MMM D, HH:mm";
    }
    const result = [];
    for (let i = 0; i < count; i += every) {
      result.push({x: xOf(start + i * size), text: moment(start + i * size).format(format)});
    }
    return result;
  }, [count, innerWidth, start, end, size, xOf]);

  const inWindow = (index) => {
    if (!timeWindow) return true;
    const bucketStart = start + index * size;
    return bucketStart >= timeWindow.from && bucketStart < timeWindow.to;
  }

  const readout = () => {
    if (!timeWindow) {
      return null;
    }
    const buckets = Math.round((timeWindow.to - timeWindow.from) / size);
    return (
        <span>
          {formatWindowTime(timeWindow.from)} to {formatWindowTime(timeWindow.to)}
          <span className="text-muted"> ({formatDurationMs(buckets * size)})</span>
        </span>
    );
  }

  const tooltip = () => {
    if (!hovered || !timeline.buckets[hovered.index]) return null;
    const bucket = timeline.buckets[hovered.index];
    const bucketStart = start + hovered.index * size;
    return (
        <div style={{
          ...tooltipStyle(colors),
          left: Math.min(hovered.x + 12, Math.max(0, width - 230)) + "px",
          top: "-6px",
          transform: "translateY(-100%)"
        }}>
          <div style={{fontWeight: "bold"}}>
            {moment(bucketStart).format("YYYY-MM-DD HH:mm")} to {moment(bucketStart + size).format(
                moment(bucketStart).isSame(bucketStart + size, "day") ? "HH:mm" : "YYYY-MM-DD HH:mm")}
          </div>
          {CLASSES.map(([key, title]) => (
              <div key={key}>
                {title}: {formatCount(bucket["sessions_" + key])} {bucket["sessions_" + key] === 1 ? "session" : "sessions"}, {formatBytes(bucket["bytes_" + key])}
              </div>
          ))}
        </div>
    );
  }

  return (
      <div>
        <div className="d-flex flex-wrap align-items-center mb-2">
          <div className="small text-muted text-uppercase me-3">Time Window</div>
          <div className="me-auto">{readout()}</div>

          <div className="d-flex align-items-center me-2">
            <label htmlFor="topology-window-metric" className="me-2 small text-muted mb-0">Bars</label>
            <select id="topology-window-metric" className="form-select form-select-sm w-auto" value={metric}
                    onChange={(e) => setMetric(e.target.value)}>
              <option value="bytes">Traffic</option>
              <option value="sessions">Sessions</option>
            </select>
          </div>
          <div className="d-flex align-items-center me-2">
            <label htmlFor="topology-window-speed" className="me-2 small text-muted mb-0">Speed</label>
            <select id="topology-window-speed" className="form-select form-select-sm w-auto" value={speed}
                    onChange={(e) => setSpeed(e.target.value)}>
              {SPEEDS.map(([value, title]) => <option key={value} value={value}>{title}</option>)}
            </select>
          </div>
          <div className="btn-group btn-group-sm me-2" role="group" aria-label="Replay time window">
            <button type="button" className="btn btn-outline-secondary" title="Move window back"
                    disabled={count === 0} onClick={() => { setPlaying(false); step(-1); }}>
              <i className="fa-solid fa-backward-step" />
            </button>
            <button type="button" className={"btn " + (playing ? "btn-secondary" : "btn-outline-secondary")}
                    title={playing ? "Pause replay" : "Replay: moves the window across the time range"}
                    disabled={count === 0} onClick={togglePlay}>
              <i className={"fa-solid " + (playing ? "fa-pause" : "fa-play")} />
            </button>
            <button type="button" className="btn btn-outline-secondary" title="Move window forward"
                    disabled={count === 0} onClick={() => { setPlaying(false); step(1); }}>
              <i className="fa-solid fa-forward-step" />
            </button>
            <button type="button" className={"btn " + (repeat ? "btn-secondary" : "btn-outline-secondary")}
                    title={repeat ? "The replay starts over at the end of the time range" : "The replay stops at the end of the time range"}
                    aria-pressed={repeat} onClick={() => setRepeat(!repeat)}>
              <i className="fa-solid fa-repeat" />
            </button>
          </div>
          <button type="button" className="btn btn-sm btn-outline-secondary me-2" disabled={!timeWindow}
                  onClick={() => { setPlaying(false); setTimeWindow(null); }}>
            Clear Window
          </button>
          <button type="button" className="btn btn-sm btn-outline-primary" disabled={!timeWindow}
                  title="Applies the window as the time range of the page."
                  onClick={() => { setPlaying(false); onApply(timeWindow); }}>
            Use as Time Range
          </button>
        </div>

        <div ref={containerRef} className="border rounded"
             style={{position: "relative", width: "100%", height: (CHART_HEIGHT + AXIS_HEIGHT) + "px", background: colors.background}}
             onMouseMove={onPointerMove}
             onMouseLeave={() => setHovered(null)}>
          {/* Out of the flow, so the chart follows the width of the card and never holds it open. */}
          <svg width={width} height={CHART_HEIGHT + AXIS_HEIGHT}
               style={{position: "absolute", top: 0, left: 0, display: "block", fontFamily: FONT_FAMILY}}>
            {bars.totals.map((values, i) => {
              let y = CHART_HEIGHT;
              const lit = inWindow(i);
              return (
                  <g key={i} style={{opacity: lit ? (hovered && hovered.index === i ? 1 : 0.85) : 0.25, transition: "opacity 120ms linear"}}>
                    {values.map((value, c) => {
                      if (value <= 0 || bars.max <= 0) return null;
                      const h = Math.max(1.5, value / bars.max * (CHART_HEIGHT - 8));
                      y -= h;
                      return <rect key={c} x={PADDING + i * barWidth + 0.5} y={y}
                                   width={Math.max(1, barWidth - 1)} height={h}
                                   fill={CLASSES[c][2](colors)} />;
                    })}
                  </g>
              );
            })}

            <line x1={PADDING} x2={PADDING + innerWidth} y1={CHART_HEIGHT + 0.5} y2={CHART_HEIGHT + 0.5} stroke={colors.lines} />
            {ticks.map((tick, i) => (
                <text key={i} x={tick.x + 2} y={CHART_HEIGHT + 13} fontSize="10" fill={colors.muted}>{tick.text}</text>
            ))}

            <g ref={brushGroupRef} />
          </svg>

          {tooltip()}
        </div>

        <div className="d-flex flex-wrap small text-muted mt-1">
          {CLASSES.map(([key, title, color]) => (
              <span key={key} className="me-3">
                <span style={{display: "inline-block", width: "9px", height: "9px", borderRadius: "2px", marginRight: "5px", background: color(colors)}} />
                {title}
              </span>
          ))}
          <span className="ms-auto">
            {bars.max > 0 ? "Tallest bar: " + (metric === "bytes" ? formatBytes(bars.max) : formatCount(bars.max) + " sessions") + " in " + formatDurationMs(size) : null}
          </span>
        </div>
      </div>
  )

}
