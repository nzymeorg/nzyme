import React, {useState} from "react";
import {toast} from "react-toastify";
import Dot11Service from "../../../services/Dot11Service";
import CardTitleWithControls from "../../shared/CardTitleWithControls";

const dot11Service = new Dot11Service();

const MAX_ALLOWED_SSIDS = 500;

export default function AllowedSimilarSSIDs({uuid, allowedSsids, onChange}) {

  const [ssid, setSsid] = useState("");

  const add = (e) => {
    e.preventDefault();

    dot11Service.addAllowedSimilarSSID(uuid, ssid, () => {
      toast.success("SSID allowed.");
      setSsid("");
      onChange();
    });
  }

  const remove = (e, allowed) => {
    e.preventDefault();

    if (!confirm("Really remove allowed SSID?")) {
      return;
    }

    dot11Service.deleteAllowedSimilarSSID(uuid, allowed, () => {
      toast.success("Allowed SSID removed.");
      onChange();
    });
  }

  const full = allowedSsids.length >= MAX_ALLOWED_SSIDS;

  return (
    <div className="row mt-3">
      <div className="col-xl-12 col-xxl-6">
        <div className="card">
          <div className="card-body">
            <CardTitleWithControls title="Allowed SSIDs"
                                   helpLink="https://go.nzyme.org/wifi-network-monitoring-similar-ssids-allowed" />

            <p className="mb-3">
              Allowed SSIDs never trigger the similar SSID alarm of this network. A network can have up
              to {MAX_ALLOWED_SSIDS} allowed SSIDs.
            </p>

            <form onSubmit={add}>
              <label htmlFor="allowed-ssid" className="form-label">SSID</label>
              <div className="input-group mb-3">
                <input type="text"
                       className="form-control"
                       id="allowed-ssid"
                       value={ssid}
                       maxLength={255}
                       onChange={(e) => setSsid(e.target.value)} />
                <button type="submit" className="btn btn-secondary" disabled={ssid.length === 0 || full}>
                  Allow SSID
                </button>
              </div>
            </form>

            {allowedSsids.length === 0 ? <div className="alert alert-info mb-0">No allowed SSIDs configured yet.</div> :
              <table className="table table-sm table-hover table-striped mb-0">
                <thead>
                <tr>
                  <th>SSID</th>
                  <th>&nbsp;</th>
                </tr>
                </thead>
                <tbody>
                {allowedSsids.map((allowed) => (
                  <tr key={allowed}>
                    <td><span className="machine-data">{allowed}</span></td>
                    <td className="text-end"><a href="#" onClick={(e) => remove(e, allowed)}>Remove</a></td>
                  </tr>
                ))}
                </tbody>
              </table>}
          </div>
        </div>
      </div>
    </div>
  )

}
