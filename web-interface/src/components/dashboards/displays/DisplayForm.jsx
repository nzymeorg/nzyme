import React, {useEffect, useState} from "react";
import moment from "moment-timezone";
import DashboardsService from "../../../services/DashboardsService";
import LocationsService from "../../../services/LocationsService";
import {TIME_ZONES} from "../TimeZones";

const dashboardsService = new DashboardsService();
const locationsService = new LocationsService();

const MAXIMUM_DASHBOARDS = 25;
const MAXIMUM_LISTED_DASHBOARDS = 250;


const EMPTY = {
  name: "",
  location_id: "",
  dashboards: [],
  theme: "DARK",
  light_from: "07:00",
  light_until: "19:00",
  time_zone: moment.tz.guess(),
  clock_format: "H24",
  scale_percent: "100",
  rotation_seconds: "30",
  refresh_seconds: "60",
  allowed_networks: "",
  redacted: "false",
  hide_cursor: "false",
  expires_at: ""
};

const isNumberBetween = (value, minimum, maximum) => {
  const number = Number(value);
  return String(value).trim() !== "" && Number.isInteger(number) && number >= minimum && number <= maximum;
}

export default function DisplayForm({organizationId, tenantId, initialValues = undefined, submitText, onSubmit}) {

  const [values, setValues] = useState({...EMPTY, ...(initialValues ? initialValues : {})});

  const [locations, setLocations] = useState(null);
  const [available, setAvailable] = useState(null);
  const [candidate, setCandidate] = useState("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    setAvailable(null);
    dashboardsService.findAllDashboards(organizationId, tenantId, MAXIMUM_LISTED_DASHBOARDS, 0, setAvailable);

    setLocations(null);
    locationsService.findAll(organizationId, tenantId, setLocations);
  }, [organizationId, tenantId]);

  const update = (field) => (e) => {
    setValues({...values, [field]: e.target.value});
  }

  const selectable = available ?
    available.dashboards.filter((dashboard) => !values.dashboards.some((selected) => selected.uuid === dashboard.uuid))
    : [];

  const addDashboard = () => {
    const dashboard = selectable.find((d) => d.uuid === candidate);
    if (dashboard) {
      setValues({...values, dashboards: [...values.dashboards, {uuid: dashboard.uuid, name: dashboard.name}]});
      setCandidate("");
    }
  }

  const removeDashboard = (index) => {
    setValues({...values, dashboards: values.dashboards.filter((d, i) => i !== index)});
  }

  const moveDashboard = (index, offset) => {
    const dashboards = [...values.dashboards];
    const [moved] = dashboards.splice(index, 1);
    dashboards.splice(index + offset, 0, moved);
    setValues({...values, dashboards: dashboards});
  }

  const formIsReady = () => {
    return values.name.trim().length > 0
      && isNumberBetween(values.scale_percent, 50, 200)
      && isNumberBetween(values.rotation_seconds, 5, 3600)
      && isNumberBetween(values.refresh_seconds, 10, 3600)
      && (values.theme !== "SCHEDULED"
        || (values.light_from && values.light_until && values.light_from !== values.light_until))
      && !isSubmitting;
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    const scheduled = values.theme === "SCHEDULED";

    const payload = {
      name: values.name.trim(),
      location_id: values.location_id ? values.location_id : null,
      dashboards: values.dashboards.map((dashboard) => dashboard.uuid),
      theme: values.theme,
      light_from: scheduled ? values.light_from : null,
      light_until: scheduled ? values.light_until : null,
      time_zone: values.time_zone,
      clock_format: values.clock_format,
      scale_percent: Number(values.scale_percent),
      rotation_seconds: Number(values.rotation_seconds),
      refresh_seconds: Number(values.refresh_seconds),
      redacted: values.redacted === "true",
      hide_cursor: values.hide_cursor === "true",
      allowed_networks: values.allowed_networks.split("\n").map((network) => network.trim()).filter((network) => network !== ""),
      expires_at: values.expires_at ? moment(values.expires_at).toISOString() : null
    };

    onSubmit(payload, (error) => {
      setIsSubmitting(false);
      if (error && error.response && error.response.status === 422 && error.response.data) {
        setSubmitError(error.response.data.message);
      } else {
        setSubmitError("Could not save the display.");
      }
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-name" className="form-label">Name</label>
          <input type="text" className="form-control" id="display-name" maxLength={128}
                 value={values.name} onChange={update("name")} />
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="display-location" className="form-label">Location <small>Optional</small></label>
          <select className="form-select" id="display-location" value={locations ? values.location_id : ""}
                  onChange={update("location_id")} disabled={!locations}>
            <option value="">{locations ? "No location" : "Loading ..."}</option>
            {locations ? locations.map((location) => {
              return <option key={location.id} value={location.id}>{location.name}</option>
            }) : null}
          </select>
          <div className="form-text">Where the screen is located.</div>
        </div>
      </div>

      <h5 className="mt-2">Dashboards</h5>

      <div className="mb-3">
        {values.dashboards.length === 0 ?
          <div className="alert alert-secondary">No dashboard is assigned to this display.</div>
          :
          <table className="table table-sm">
            <tbody>
            {values.dashboards.map((dashboard, i) => {
              return (
                <tr key={dashboard.uuid}>
                  <td style={{width: 40}}>{i + 1}</td>
                  <td>{dashboard.name}</td>
                  <td className="text-end">
                    <button type="button" className="btn btn-sm btn-outline-secondary me-1"
                            disabled={i === 0} onClick={() => moveDashboard(i, -1)}>
                      Up
                    </button>
                    <button type="button" className="btn btn-sm btn-outline-secondary me-1"
                            disabled={i === values.dashboards.length - 1} onClick={() => moveDashboard(i, 1)}>
                      Down
                    </button>
                    <button type="button" className="btn btn-sm btn-outline-danger"
                            onClick={() => removeDashboard(i)}>
                      Remove
                    </button>
                  </td>
                </tr>
              )
            })}
            </tbody>
          </table>}

        <div className="input-group">
          <select className="form-select" id="display-dashboard" aria-label="Dashboard"
                  value={candidate} onChange={(e) => setCandidate(e.target.value)}
                  disabled={!available || values.dashboards.length >= MAXIMUM_DASHBOARDS}>
            <option value="">{available ? "Select a dashboard" : "Loading ..."}</option>
            {selectable.map((dashboard) => {
              return <option key={dashboard.uuid} value={dashboard.uuid}>{dashboard.name}</option>
            })}
          </select>
          <button type="button" className="btn btn-secondary" disabled={!candidate} onClick={addDashboard}>
            Add Dashboard
          </button>
        </div>
        <div className="form-text">
          The display rotates through up to {MAXIMUM_DASHBOARDS} dashboards in this order.
          {available && available.total > MAXIMUM_LISTED_DASHBOARDS ?
            " Only the first " + MAXIMUM_LISTED_DASHBOARDS + " dashboards are listed." : null}
        </div>
      </div>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-rotation" className="form-label">Rotation Interval</label>
          <div className="input-group">
            <input type="number" className="form-control" id="display-rotation" min={5} max={3600}
                   value={values.rotation_seconds} onChange={update("rotation_seconds")} />
            <span className="input-group-text">seconds</span>
          </div>
          <div className="form-text">Between 5 and 3600 seconds.</div>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="display-refresh" className="form-label">Refresh Interval</label>
          <div className="input-group">
            <input type="number" className="form-control" id="display-refresh" min={10} max={3600}
                   value={values.refresh_seconds} onChange={update("refresh_seconds")} />
            <span className="input-group-text">seconds</span>
          </div>
          <div className="form-text">Between 10 and 3600 seconds.</div>
        </div>
      </div>

      <h5 className="mt-2">Presentation</h5>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-theme" className="form-label">Theme</label>
          <select className="form-select" id="display-theme" value={values.theme} onChange={update("theme")}>
            <option value="DARK">Dark</option>
            <option value="LIGHT">Light</option>
            <option value="SCHEDULED">Light during the day, dark at night</option>
          </select>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="display-scale" className="form-label">Scale</label>
          <div className="input-group">
            <input type="number" className="form-control" id="display-scale" min={50} max={200}
                   value={values.scale_percent} onChange={update("scale_percent")} />
            <span className="input-group-text">%</span>
          </div>
          <div className="form-text">Between 50 and 200 percent.</div>
        </div>
      </div>

      {values.theme === "SCHEDULED" ?
        <div className="row">
          <div className="col-md-6 mb-3">
            <label htmlFor="display-light-from" className="form-label">Light Theme From</label>
            <input type="time" className="form-control" id="display-light-from"
                   value={values.light_from} onChange={update("light_from")} />
          </div>

          <div className="col-md-6 mb-3">
            <label htmlFor="display-light-until" className="form-label">Light Theme Until</label>
            <input type="time" className="form-control" id="display-light-until"
                   value={values.light_until} onChange={update("light_until")} />
            <div className="form-text">Both times are in the time zone of the display.</div>
          </div>
        </div> : null}

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-time-zone" className="form-label">Time Zone</label>
          <select className="form-select" id="display-time-zone" value={values.time_zone}
                  onChange={update("time_zone")}>
            {TIME_ZONES.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
          </select>
          <div className="form-text">
            Used for the theme schedule and for the clock of a display without a dashboard.
          </div>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="display-clock-format" className="form-label">Clock Format</label>
          <select className="form-select" id="display-clock-format" value={values.clock_format}
                  onChange={update("clock_format")}>
            <option value="H24">24-hour</option>
            <option value="H12">12-hour</option>
          </select>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-hide-cursor" className="form-label">Mouse Pointer</label>
          <select className="form-select" id="display-hide-cursor" value={values.hide_cursor}
                  onChange={update("hide_cursor")}>
            <option value="false">Shown</option>
            <option value="true">Hidden</option>
          </select>
          <div className="form-text">Hide it on screens that nobody operates.</div>
        </div>
      </div>

      <h5 className="mt-2">Access</h5>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-redacted" className="form-label">Addresses and Names</label>
          <select className="form-select" id="display-redacted" value={values.redacted} onChange={update("redacted")}>
            <option value="false">Shown</option>
            <option value="true">Hidden</option>
          </select>
          <div className="form-text">Hide them on screens that visitors can see.</div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="display-networks" className="form-label">Allowed Networks <small>Optional</small></label>
          <textarea className="form-control font-monospace" id="display-networks" rows={3}
                    placeholder="10.0.0.0/8"
                    value={values.allowed_networks} onChange={update("allowed_networks")} />
          <div className="form-text">
            IP addresses or CIDR networks the display may connect from, one per line.
          </div>
        </div>

        <div className="col-md-6 mb-3">
          <label htmlFor="display-expires-at" className="form-label">Expires At <small>Optional</small></label>
          <input type="datetime-local" className="form-control" id="display-expires-at"
                 value={values.expires_at} onChange={update("expires_at")} />
          <div className="form-text">
            The display link stops working at this time, in the time zone of this browser.
          </div>
        </div>
      </div>

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>
    </form>
  )

}
