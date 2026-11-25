import React, {useState} from "react";
import L4Service from "../../../../services/ethernet/L4Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import FormSubmitErrorMessage from "../../../misc/FormSubmitErrorMessage";
import {apiErrorMessage, isValidCIDR} from "../../../../util/Tools";

const l4Service = new L4Service();

export default function L4InternalNetworks({config, organizationId, tenantId, setRevision}) {

  const [network, setNetwork] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  if (!config) {
    return <LoadingSpinner />
  }

  const networks = config.internal_networks;
  const limitReached = networks.length >= config.internal_networks_limit;
  const cleaned = network.trim();

  const formIsReady = () => {
    return cleaned.includes("/") && isValidCIDR(cleaned) && !limitReached && !isSubmitting;
  }

  const save = (newNetworks, onSuccess) => {
    setIsSubmitting(true);
    setSubmitError(null);

    l4Service.updateInternalNetworks(newNetworks, organizationId, tenantId, () => {
      setIsSubmitting(false);
      onSuccess();
      setRevision((r) => r + 1);
    }, (error) => {
      setIsSubmitting(false);
      setSubmitError(apiErrorMessage(error, "Could not save the internal networks."));
    });
  }

  const add = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    save([...networks, cleaned], () => setNetwork(""));
  }

  const remove = (removed) => {
    if (!confirm("Really remove this internal network? Addresses in it are recorded as external. Records that " +
        "are stored already are updated in the background.")) {
      return;
    }

    save(networks.filter((n) => n !== removed), () => {});
  }

  return (
    <React.Fragment>
      {networks.length === 0
        ? <div className="alert alert-info mb-3">No internal networks are configured.</div>
        : (
          <table className="table table-sm table-hover table-striped mb-3">
            <thead>
            <tr>
              <th>Network</th>
              <th>Actions</th>
            </tr>
            </thead>
            <tbody>
            {networks.map((n) => (
              <tr key={n}>
                <td><span className="machine-data">{n}</span></td>
                <td>
                  <button type="button" className="btn btn-link p-0 text-danger" disabled={isSubmitting}
                          onClick={() => remove(n)}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            </tbody>
          </table>
        )}

      <form onSubmit={add}>
        <label htmlFor="internal-network" className="form-label">Add Network</label>
        <div className="input-group">
          <input type="text" className="form-control" id="internal-network" autoComplete="off"
                 maxLength={64} placeholder="2001:db8:1200::/48" disabled={limitReached}
                 value={network} onChange={(e) => setNetwork(e.target.value)} />
          <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
            {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : "Add Network"}
          </button>
        </div>
        <div className="form-text">
          IPv4 or IPv6 network in CIDR notation. An IPv4 network can be a /8 at most and an IPv6 network a /16.
          Up to {config.internal_networks_limit} networks.
        </div>

        <FormSubmitErrorMessage message={submitError} />
      </form>
    </React.Fragment>
  )

}
