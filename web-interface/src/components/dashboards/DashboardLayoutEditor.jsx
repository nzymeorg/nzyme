import React, {useRef, useState} from "react";
import moment from "moment-timezone";
import DashboardGrid from "./screen/DashboardGrid";
import sampleData from "./screen/widgets/SampleData";
import {TIME_ZONES} from "./TimeZones";
import TimeZoneSelect from "./TimeZoneSelect";
import {
  ALERT_METRICS, firstFreePosition, fits, GRID_COLUMNS, GRID_ROWS, MAXIMUM_TEXT_LENGTH, MAXIMUM_WIDGETS, SUBSYSTEMS,
  takesSubsystem, TEXT_SIZES, TOPOLOGY_CONNECTIONS, WIDGET_TYPES
} from "./screen/widgets/WidgetCatalog";
import Filters from "../shared/filtering/Filters";
import {filtersToQueryParameters, queryParametersToFilters} from "../shared/filtering/FilterQueryParameters";
import {L4_SESSIONS_FILTER_FIELDS, L4_SESSIONS_QUICK_FILTERS} from "../ethernet/l4/L4SessionFilterFields";
import {EXTERNAL_LEVELS, INTERNAL_LEVELS} from "../ethernet/topology/TopologyData";

const newId = () => "w-" + Math.random().toString(36).substring(2, 10);

export default function DashboardLayoutEditor({widgets, setWidgets, windowMinutes, metrics, alertsAllowed,
                                                subsystems, topologyAllowed}) {

  const canvas = useRef(null);
  const drag = useRef(null);

  const [selectedId, setSelectedId] = useState(null);
  const [addType, setAddType] = useState("METRIC");

  const selected = widgets.find((widget) => widget.id === selectedId);

  const selectableMetrics = metrics.filter((m) => m.available && (alertsAllowed || !ALERT_METRICS.includes(m.metric)));
  const typeAllowed = (type) => {
    if (type === "STATUS" || type === "ALERTS" || type === "LOCATIONS") {
      return alertsAllowed;
    }

    if (type === "TOPOLOGY") {
      return topologyAllowed;
    }

    return type !== "METRIC" || selectableMetrics.length > 0;
  }

  const replace = (changed) => {
    setWidgets(widgets.map((widget) => widget.id === changed.id ? changed : widget));
  }

  const cellOf = (e) => {
    const rect = canvas.current.getBoundingClientRect();

    return {
      x: (e.clientX - rect.left)/(rect.width/GRID_COLUMNS),
      y: (e.clientY - rect.top)/(rect.height/GRID_ROWS)
    };
  }

  const onPointerDown = (e, widget, mode) => {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);

    const cell = cellOf(e);
    drag.current = {id: widget.id, mode: mode, offsetX: cell.x - widget.x, offsetY: cell.y - widget.y};
    setSelectedId(widget.id);
  }

  const onPointerMove = (e) => {
    if (!drag.current) {
      return;
    }

    const widget = widgets.find((w) => w.id === drag.current.id);
    if (!widget) {
      return;
    }

    const cell = cellOf(e);
    const candidate = drag.current.mode === "move"
      ? {...widget, x: Math.round(cell.x - drag.current.offsetX), y: Math.round(cell.y - drag.current.offsetY)}
      : {
        ...widget,
        w: Math.max(WIDGET_TYPES[widget.type].minimumW, Math.ceil(cell.x - widget.x)),
        h: Math.max(WIDGET_TYPES[widget.type].minimumH, Math.ceil(cell.y - widget.y))
      };

    const changed = candidate.x !== widget.x || candidate.y !== widget.y
      || candidate.w !== widget.w || candidate.h !== widget.h;

    if (changed && fits(candidate, widgets)) {
      replace(candidate);
    }
  }

  const onPointerUp = () => {
    drag.current = null;
  }

  // A widget is added at its usual size, or at its smallest size where the grid is nearly full.
  const size = WIDGET_TYPES[addType];
  const full = firstFreePosition(size.w, size.h, widgets);
  const position = full || firstFreePosition(size.minimumW, size.minimumH, widgets);
  const canAdd = widgets.length < MAXIMUM_WIDGETS && position !== null && typeAllowed(addType);

  const add = () => {
    if (!canAdd) {
      return;
    }

    const widget = {
      id: newId(),
      type: addType,
      x: position.x,
      y: position.y,
      w: full ? size.w : size.minimumW,
      h: full ? size.h : size.minimumH,
      options: defaultOptions(addType)
    };

    setWidgets([...widgets, widget]);
    setSelectedId(widget.id);
  }

  const defaultOptions = (type) => {
    switch (type) {
      case "METRIC":
        return {metric: selectableMetrics[0].metric};
      case "CLOCK":
        return {time_zone: TIME_ZONES.includes(moment.tz.guess()) ? moment.tz.guess() : "UTC"};
      case "TEXT":
        return {text: "Text", size: "MEDIUM"};
      case "TOPOLOGY":
        return {internal_level: "HOST", external_level: "HOST", connections: 100};
      default:
        return {};
    }
  }

  const remove = () => {
    setWidgets(widgets.filter((widget) => widget.id !== selectedId));
    setSelectedId(null);
  }

  const setOption = (key, value) => {
    const widgetOptions = {...selected.options};
    if (value) {
      widgetOptions[key] = value;
    } else {
      delete widgetOptions[key];
    }

    replace({...selected, options: widgetOptions});
  }

  return (
    <React.Fragment>
      <div className="input-group mb-2" style={{maxWidth: 420}}>
        <select className="form-select" id="dashboard-add-widget-type" aria-label="Widget Type"
                value={addType} onChange={(e) => setAddType(e.target.value)}>
          {Object.keys(WIDGET_TYPES).map((type) => {
            return <option key={type} value={type} disabled={!typeAllowed(type)}>{WIDGET_TYPES[type].title}</option>
          })}
        </select>
        <button type="button" className="btn btn-secondary" disabled={!canAdd} onClick={add}>Add Widget</button>
      </div>

      <div className="dashboard-canvas" ref={canvas} onPointerDown={() => setSelectedId(null)}>
        <div className="dashboard-canvas-lines"></div>

        <DashboardGrid widgets={widgets}
                       data={sampleData(widgets, metrics, windowMinutes)}
                       now={Date.now()}
                       windowMinutes={windowMinutes} />

        <div className="dashboard-grid">
          {widgets.map((widget) => {
            return (
              <div key={widget.id}
                   className={"dashboard-grid-cell" + (widget.id === selectedId ? " dashboard-canvas-selected" : "")}
                   data-widget-id={widget.id}
                   style={{
                     gridColumn: `${widget.x + 1} / span ${widget.w}`,
                     gridRow: `${widget.y + 1} / span ${widget.h}`
                   }}
                   onPointerDown={(e) => onPointerDown(e, widget, "move")}
                   onPointerMove={onPointerMove}
                   onPointerUp={onPointerUp}
                   onPointerCancel={onPointerUp}>
                {widget.id === selectedId ?
                  <div className="dashboard-canvas-resize"
                       onPointerDown={(e) => onPointerDown(e, widget, "resize")}
                       onPointerMove={onPointerMove}
                       onPointerUp={onPointerUp}
                       onPointerCancel={onPointerUp}></div> : null}
              </div>
            )
          })}
        </div>
      </div>

      <div className="form-text mb-3">
        Drag a widget to move it and drag its lower right corner to resize it. The widgets show sample data here.
      </div>

      {selected ?
        <div className="row">
          <div className="col-md-5 mb-3">
            <label htmlFor="dashboard-widget-title" className="form-label">
              Title of Selected Widget <small>Optional</small>
            </label>
            <input type="text" className="form-control" id="dashboard-widget-title" maxLength={64}
                   value={selected.options.title ? selected.options.title : ""}
                   onChange={(e) => setOption("title", e.target.value)} />
          </div>

          <div className="col-md-3 mb-3 d-flex align-items-end">
            <button type="button" className="btn btn-outline-danger" onClick={remove}>Remove Widget</button>
          </div>

          <div className="w-100"></div>

          {selected.type === "METRIC" ?
            <div className="col-md-4 mb-3">
              <label htmlFor="dashboard-widget-metric" className="form-label">Metric</label>
              <select className="form-select" id="dashboard-widget-metric" value={selected.options.metric}
                      onChange={(e) => setOption("metric", e.target.value)}>
                {metrics.map((metric) => {
                  return (
                    <option key={metric.metric} value={metric.metric}
                            disabled={!selectableMetrics.includes(metric) && metric.metric !== selected.options.metric}>
                      {metric.title}
                    </option>
                  )
                })}
              </select>
            </div> : null}

          {takesSubsystem(selected) ?
            <div className="col-md-4 mb-3">
              <label htmlFor="dashboard-widget-subsystem" className="form-label">Alerts</label>
              <select className="form-select" id="dashboard-widget-subsystem" disabled={!alertsAllowed}
                      value={selected.options.subsystem ? selected.options.subsystem : ""}
                      onChange={(e) => setOption("subsystem", e.target.value)}>
                <option value="">All</option>
                {SUBSYSTEMS.map((subsystem) => {
                  return (
                    <option key={subsystem.key} value={subsystem.key}
                            disabled={!subsystems.includes(subsystem.key) && subsystem.key !== selected.options.subsystem}>
                      {subsystem.title} only
                    </option>
                  )
                })}
              </select>
            </div> : null}

          {selected.type === "CLOCK" ?
            <div className="col-md-4 mb-3">
              <label htmlFor="dashboard-widget-time-zone" className="form-label">Time Zone</label>
              <TimeZoneSelect key={selected.id}
                              id="dashboard-widget-time-zone"
                              value={selected.options.time_zone}
                              onChange={(zone) => setOption("time_zone", zone)} />
            </div> : null}

          {selected.type === "TEXT" ?
            <div className="col-md-4 mb-3">
              <label htmlFor="dashboard-widget-text-size" className="form-label">Text Size</label>
              <select className="form-select" id="dashboard-widget-text-size" value={selected.options.size}
                      onChange={(e) => setOption("size", e.target.value)}>
                {TEXT_SIZES.map((size) => <option key={size.key} value={size.key}>{size.title}</option>)}
              </select>
            </div> : null}

          {selected.type === "TOPOLOGY" ?
            <React.Fragment>
              <div className="col-md-4 mb-3">
                <label htmlFor="dashboard-widget-internal-level" className="form-label">Internal Nodes</label>
                <select className="form-select" id="dashboard-widget-internal-level"
                        value={selected.options.internal_level}
                        onChange={(e) => setOption("internal_level", e.target.value)}>
                  {INTERNAL_LEVELS.map(([level, title]) => <option key={level} value={level}>{title}</option>)}
                </select>
              </div>

              <div className="col-md-4 mb-3">
                <label htmlFor="dashboard-widget-external-level" className="form-label">External Nodes</label>
                <select className="form-select" id="dashboard-widget-external-level"
                        value={selected.options.external_level}
                        onChange={(e) => setOption("external_level", e.target.value)}>
                  {EXTERNAL_LEVELS.map(([level, title]) => <option key={level} value={level}>{title}</option>)}
                </select>
              </div>

              <div className="col-md-4 mb-3">
                <label htmlFor="dashboard-widget-connections" className="form-label">Connections</label>
                <select className="form-select" id="dashboard-widget-connections"
                        value={selected.options.connections}
                        onChange={(e) => setOption("connections", parseInt(e.target.value, 10))}>
                  {TOPOLOGY_CONNECTIONS.map((limit) => <option key={limit} value={limit}>Up to {limit}</option>)}
                </select>
              </div>

              {/* Enter in a filter value must not save the dashboard. */}
              <div className="col-12 mb-3" id="dashboard-widget-filters"
                   onKeyDown={(e) => { if (e.key === "Enter") e.preventDefault(); }}>
                <label className="form-label">Filters <small>Optional</small></label>
                <Filters key={selected.id}
                         embedded={true}
                         filters={queryParametersToFilters(selected.options.filters, L4_SESSIONS_FILTER_FIELDS)}
                         setFilters={(filters) => setOption("filters", filters && Object.keys(filters).length > 0
                           ? JSON.stringify(filtersToQueryParameters(filters)) : null)}
                         fields={L4_SESSIONS_FILTER_FIELDS}
                         quickFilters={L4_SESSIONS_QUICK_FILTERS} />
                <div className="form-text">
                  The topology shows the connections of the last 30 seconds that match all filters.
                </div>
              </div>
            </React.Fragment> : null}

          {selected.type === "TEXT" ?
            <div className="col-12 mb-3">
              <label htmlFor="dashboard-widget-text" className="form-label">Text</label>
              <textarea className="form-control" id="dashboard-widget-text" rows={4} maxLength={MAXIMUM_TEXT_LENGTH}
                        value={selected.options.text ? selected.options.text : ""}
                        onChange={(e) => setOption("text", e.target.value)} />
              <div className="form-text">
                The text is shown as it is, also on displays that hide addresses and names.
              </div>
            </div> : null}
        </div> : null}
    </React.Fragment>
  )

}
