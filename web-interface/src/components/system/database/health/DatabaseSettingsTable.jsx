import React, {useState} from "react";

function scopeLabel(scope) {
  switch (scope) {
    case "SERVER": return <span title="Lives in postgresql.conf. Only the database administrator can change it.">postgresql.conf</span>;
    case "SESSION": return <span title="Nzyme sets this on its own connections.">Nzyme connection</span>;
    case "TABLE": return <span title="Nzyme sets this per table.">Nzyme table</span>;
    default: return scope;
  }
}

function status(setting) {
  if (setting.pending_restart) {
    return <span className="text-warning" title="postgresql.conf already has a new value. PostgreSQL applies it at the next restart.">Waiting for PostgreSQL restart</span>
  }

  if (setting.ok) {
    return <span className="text-success">OK</span>
  }

  return setting.severity === "REQUIRED"
    ? <span className="text-danger">Change required</span>
    : <span className="text-warning">Recommended</span>
}

export default function DatabaseSettingsTable({settings}) {

  const [showAll, setShowAll] = useState(false);

  const required = settings.filter((s) => !s.ok && s.severity === "REQUIRED" && s.config_lines);
  const recommended = settings.filter((s) => !s.ok && s.severity === "RECOMMENDED" && s.config_lines);
  const visible = showAll ? settings : settings.filter((s) => !s.ok);

  const configBlock = (list) => list.map((s) => s.config_lines).join("\n");

  return (
    <React.Fragment>
      <p>
        Settings marked <em>postgresql.conf</em> must be changed by the database administrator.
      </p>

      {required.length > 0 || recommended.length > 0 ?
        <div className="row mb-3">
          {required.length > 0 ?
            <div className={recommended.length > 0 ? "col-lg-6" : "col-12"}>
              <div className="card">
                <div className="card-body">
                  <h3>Required changes to postgresql.conf</h3>
                  <pre className="mb-0" style={{whiteSpace: "pre-wrap"}}>{configBlock(required)}</pre>
                </div>
              </div>
            </div> : null}
          {recommended.length > 0 ?
            <div className={required.length > 0 ? "col-lg-6" : "col-12"}>
              <div className="card">
                <div className="card-body">
                  <h3>Recommended changes to postgresql.conf</h3>
                  <pre className="mb-0" style={{whiteSpace: "pre-wrap"}}>{configBlock(recommended)}</pre>
                </div>
              </div>
            </div> : null}
        </div>
        : <div className="alert alert-success">All server settings meet the recommendations.</div>}

      {visible.length === 0 && !showAll ? null :
        <table className="table table-sm table-hover table-striped mb-2">
          <thead>
          <tr>
            <th>Setting</th>
            <th>Current</th>
            <th>Recommended</th>
            <th>Status</th>
            <th>Set in</th>
          </tr>
          </thead>
          <tbody>
          {visible.map((s) => (
            <tr key={s.name}>
              <td>
                <span className="machine-data">{s.name}</span>
                <div className="text-muted small">{s.description}</div>
              </td>
              <td className="machine-data">{s.current_value}</td>
              <td>{s.recommended_value}</td>
              <td className="text-nowrap">{status(s)}</td>
              <td>{scopeLabel(s.scope)}</td>
            </tr>
          ))}
          </tbody>
        </table>}

      <button className="btn btn-sm btn-outline-secondary" onClick={() => setShowAll(!showAll)}>
        {showAll ? "Show only settings that need attention" : "Show all " + settings.length + " settings"}
      </button>
    </React.Fragment>
  )

}
