import React, {useEffect, useState} from "react";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../misc/LoadingSpinner";
import DashboardsService from "../../services/DashboardsService";
import DashboardLayoutEditor from "./DashboardLayoutEditor";
import {TEMPLATES, WINDOWS, widgetShowsAlerts} from "./screen/widgets/WidgetCatalog";

const dashboardsService = new DashboardsService();

const SCOPES = {
  ALL: "All taps",
  LOCATIONS: "All taps at selected locations",
  TAPS: "Selected taps"
};

export default function DashboardForm({initialValues = undefined, submitText, onSubmit}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [options, setOptions] = useState(null);

  const [name, setName] = useState(initialValues ? initialValues.name : "");
  const [description, setDescription] = useState(
    initialValues && initialValues.description ? initialValues.description : ""
  );
  const [scopeType, setScopeType] = useState(initialValues ? initialValues.scope_type : null);
  const [scopeIds, setScopeIds] = useState(initialValues ? initialValues.scope.map((item) => item.id) : []);
  const [windowMinutes, setWindowMinutes] = useState(initialValues ? initialValues.window_minutes : 1440);
  const [template, setTemplate] = useState(null);
  const [widgets, setWidgets] = useState(initialValues ? initialValues.widgets : null);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    setOptions(null);
    dashboardsService.findScopeOptions(organizationId, tenantId, setOptions);
  }, [organizationId, tenantId]);

  const templateWidgets = (key, available) => {
    const metrics = available.metrics.filter((metric) => metric.available).map((metric) => metric.metric);

    return TEMPLATES.find((t) => t.key === key).widgets
      .filter((widget) => widget.type !== "METRIC" || metrics.includes(widget.options.metric));
  }

  const templateAllowed = (t) => {
    return (options.can_show_alerts || !t.widgets.some(widgetShowsAlerts))
      && (!t.subsystem || options.subsystems.includes(t.subsystem));
  }

  useEffect(() => {
    if (!options || initialValues) {
      return;
    }

    const first = TEMPLATES.find(templateAllowed);
    setTemplate(first.key);
    setWidgets(templateWidgets(first.key, options));
    setScopeType(options.all_taps ? "ALL" : (options.locations.length > 0 ? "LOCATIONS" : "TAPS"));
  }, [options]);

  if (!options || widgets === null || scopeType === null) {
    return <LoadingSpinner />
  }

  // The saved scope can hold items the editing user cannot choose. They stay listed and can only be removed.
  const scopeItems = (selectable, saved) => {
    const items = [...selectable];
    saved.forEach((item) => {
      if (!items.some((existing) => existing.id === item.id)) {
        items.push({...item, locked: true});
      }
    });

    return items.sort((a, b) => a.name.localeCompare(b.name));
  }

  const savedScope = initialValues && initialValues.scope_type === scopeType ? initialValues.scope : [];
  const items = scopeType === "LOCATIONS" ? scopeItems(options.locations, savedScope)
    : (scopeType === "TAPS" ? scopeItems(options.taps, savedScope) : []);

  const scopeAllowed = (type) => {
    if (initialValues && initialValues.scope_type === type) {
      return true;
    }

    return type === "ALL" ? options.all_taps : (type === "LOCATIONS" ? options.locations.length > 0 : true);
  }

  const changeScopeType = (type) => {
    setScopeType(type);
    setScopeIds(initialValues && initialValues.scope_type === type ? initialValues.scope.map((item) => item.id) : []);
  }

  const toggleScopeId = (id) => {
    setScopeIds(scopeIds.includes(id) ? scopeIds.filter((existing) => existing !== id) : [...scopeIds, id]);
  }

  const changeTemplate = (key) => {
    setTemplate(key);
    setWidgets(templateWidgets(key, options));
  }

  const formIsReady = () => {
    return name.trim().length > 0 && (scopeType === "ALL" || scopeIds.length > 0)
      && widgets.every((widget) => widget.type !== "TEXT" || (widget.options.text && widget.options.text.trim()))
      && !isSubmitting;
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    const payload = {
      name: name.trim(),
      description: description,
      scope_type: scopeType,
      scope_ids: scopeType === "ALL" ? [] : scopeIds,
      window_minutes: windowMinutes,
      widgets: widgets
    };

    onSubmit(payload, (error) => {
      setIsSubmitting(false);
      if (error && error.response && error.response.status === 422 && error.response.data) {
        setSubmitError(error.response.data.message);
      } else {
        setSubmitError("Could not save the dashboard.");
      }
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="mb-3">
        <label htmlFor="dashboard-name" className="form-label">Name</label>
        <input type="text" className="form-control" id="dashboard-name" maxLength={128}
               value={name} onChange={(e) => setName(e.target.value)} />
        <div className="form-text">The name is shown on the displays that show this dashboard.</div>
      </div>

      <div className="mb-3">
        <label htmlFor="dashboard-description" className="form-label">Description <small>Optional</small></label>
        <textarea className="form-control" id="dashboard-description" rows={2}
                  value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>

      <h5 className="mt-2">Data</h5>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="dashboard-scope" className="form-label">Taps</label>
          <select className="form-select" id="dashboard-scope" value={scopeType}
                  onChange={(e) => changeScopeType(e.target.value)}>
            {Object.keys(SCOPES).map((type) => {
              return <option key={type} value={type} disabled={!scopeAllowed(type)}>{SCOPES[type]}</option>
            })}
          </select>
          <div className="form-text">
            Everyone who can view the dashboard sees the data of all taps it covers.
          </div>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="dashboard-window" className="form-label">Time Window</label>
          <select className="form-select" id="dashboard-window" value={windowMinutes}
                  onChange={(e) => setWindowMinutes(parseInt(e.target.value, 10))}>
            {WINDOWS.map((w) => <option key={w.minutes} value={w.minutes}>{w.title}</option>)}
          </select>
          <div className="form-text">Metrics cover this time and are compared to the same time before it.</div>
        </div>
      </div>

      {scopeType !== "ALL" ?
        <div className="mb-3" id="dashboard-scope-items">
          {items.length === 0 ?
            <div className="alert alert-secondary mb-0">
              {scopeType === "LOCATIONS" ? "No locations to choose from." : "No taps to choose from."}
            </div>
            : items.map((item) => {
              return (
                <div className="form-check" key={item.id}>
                  <input className="form-check-input" type="checkbox" id={"dashboard-scope-" + item.id}
                         checked={scopeIds.includes(item.id)}
                         disabled={item.locked && !scopeIds.includes(item.id)}
                         onChange={() => toggleScopeId(item.id)} />
                  <label className="form-check-label" htmlFor={"dashboard-scope-" + item.id}>{item.name}</label>
                </div>
              )
            })}
          {scopeType === "LOCATIONS" ?
            <div className="form-text">
              Taps that are placed at a selected location later are included automatically.
            </div> : null}
        </div> : null}

      <h5 className="mt-2">Layout</h5>

      {initialValues ? null :
        <div className="mb-3" style={{maxWidth: 420}}>
          <label htmlFor="dashboard-template" className="form-label">Template</label>
          <select className="form-select" id="dashboard-template" value={template}
                  onChange={(e) => changeTemplate(e.target.value)}>
            {TEMPLATES.map((t) => {
              return <option key={t.key} value={t.key} disabled={!templateAllowed(t)}>{t.title}</option>
            })}
          </select>
        </div>}

      <DashboardLayoutEditor key={template}
                             widgets={widgets}
                             setWidgets={setWidgets}
                             windowMinutes={windowMinutes}
                             metrics={options.metrics}
                             alertsAllowed={options.can_show_alerts}
                             subsystems={options.subsystems}
                             topologyAllowed={options.subsystems.includes("ETHERNET") || initialValues !== undefined
                               && initialValues.widgets.some((widget) => widget.type === "TOPOLOGY")} />

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>
    </form>
  )

}
