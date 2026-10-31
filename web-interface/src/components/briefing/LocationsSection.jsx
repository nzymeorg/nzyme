import React from "react";
import {Link} from "react-router-dom";
import BriefingCard from "./BriefingCard";
import {resolveBriefingLink} from "./briefingLinks";
import {plural, subsystemName} from "./briefingFormat";
import LocationWeatherCondition from "../locations/shared/LocationWeatherCondition";
import LocationTemperature from "../locations/shared/LocationTemperature";
import LocationWind from "../locations/shared/LocationWind";
import numeral from "numeral";

export default function LocationsSection({locations, type, window}) {

  if (!locations || locations.length === 0) {
    return null;
  }

  const subsystem = type === "MAIN" ? null : subsystemName(type);
  const canViewAlerts = locations.some((l) => l.alert_count !== null && l.alert_count !== undefined);

  const hint = "Taps, active " + (subsystem ? subsystem + " " : "") + "detection alerts and current weather at each "
      + "location as of now, not limited to this briefing's window.";

  return (
    <div className="row mt-3">
      <div className="col-12">
        <BriefingCard title="Locations" hint={hint} internalLink={resolveBriefingLink({target: "LOCATIONS"}, window)}>
          <table className="table table-sm table-hover table-striped mb-0 briefing-locations">
            <thead>
            <tr>
              <th>Name</th>
              <th>Taps</th>
              {canViewAlerts ? <th>{subsystem ? "Active " + subsystem + " Alerts" : "Active Alerts"}</th> : null}
              <th>Weather</th>
              <th>Severe Environmental Alerts</th>
            </tr>
            </thead>
            <tbody>
            {locations.map((l) => (
              <tr key={l.id}>
                <td><Link to={resolveBriefingLink(l.link, window)}>{l.name}</Link></td>
                <td><Taps location={l} /></td>
                {canViewAlerts ? <td><Alerts count={l.alert_count} /></td> : null}
                <td><Weather environment={l.environment} /></td>
                <td><EnvironmentalAlerts environment={l.environment} /></td>
              </tr>
            ))}
            </tbody>
          </table>
        </BriefingCard>
      </div>
    </div>
  )

}

function Taps({location}) {
  if (location.taps_total === 0) {
    return <span className="text-muted">None</span>;
  }

  if (location.offline_taps.length === 0) {
    return <span>{location.taps_online} of {location.taps_total} online</span>;
  }

  return <span className="text-danger">{location.taps_online} of {location.taps_total} online</span>;
}

function Alerts({count}) {
  if (count === null || count === undefined) {
    return <span className="text-muted">n/a</span>;
  }
  if (count === 0) {
    return <span className="text-muted">None</span>;
  }
  return <span className="badge text-bg-danger">{numeral(count).format("0,0")}</span>;
}

function Weather({environment}) {
  if (!environment) {
    return <span className="text-muted">No data</span>;
  }

  return (
    <span>
      <LocationWeatherCondition environment={environment} />,{" "}
      <LocationTemperature environment={environment} />,{" "}
      <LocationWind environment={environment} />
    </span>
  );
}

function EnvironmentalAlerts({environment}) {
  if (!environment) {
    return <span className="text-muted">n/a</span>;
  }
  if (environment.alerts.length === 0) {
    return <span className="text-muted">None</span>;
  }

  const shown = environment.alerts.slice(0, 2).join(", ");
  const remaining = environment.alerts.length - 2;

  return (
    <span>
      <span className="text-danger fw-semibold">{shown}</span>
      {remaining > 0 ? <span className="text-muted"> and {remaining} more {plural(remaining, "alert", "alerts")}</span> : null}
    </span>
  );
}
