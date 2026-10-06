import React, {useState} from "react";
import UserProfileService from "../../../services/UserProfileService";
import {toast} from "react-toastify";
import moment from "moment";

const userProfileService = new UserProfileService();

export default function CreateUserApiKeyForm(props) {

  const onKeyCreated = props.onKeyCreated;

  const [name, setName] = useState("");
  const [expiryDays, setExpiryDays] = useState("90");
  const [formSubmitting, setFormSubmitting] = useState(false);

  const [createdKey, setCreatedKey] = useState(null);
  const [copiedToClipboard, setCopiedToClipboard] = useState(false);

  const formIsReady = function() {
    return name && name.trim().length > 0 && name.trim().length <= 128;
  }

  const submit = function(e) {
    e.preventDefault();

    setFormSubmitting(true);
    setCreatedKey(null);
    setCopiedToClipboard(false);

    const expiry = expiryDays === "" ? null : parseInt(expiryDays, 10);

    userProfileService.createOwnApiKey(name.trim(), expiry, function(response) {
      setFormSubmitting(false);
      setName("");
      setExpiryDays("90");
      setCreatedKey(response.data);
      toast.success("API key created.");

      if (onKeyCreated) {
        onKeyCreated();
      }
    });
  }

  const copyKey = function() {
    navigator.clipboard.writeText(createdKey.key).then(() => setCopiedToClipboard(true));
  }

  const dismissKey = function() {
    setCreatedKey(null);
    setCopiedToClipboard(false);
  }

  return (
      <React.Fragment>
        {createdKey ?
            <div className="alert alert-warning">
              <h5 className="alert-heading">
                <i className="fa fa-warning"/> Copy your new API key now
              </h5>
              <p>
                This is the only time the key will be shown. It is not stored in a recoverable form and will
                disappear as soon as you leave this page or dismiss this message.
              </p>

              <div className="input-group">
                <input type="text" className="form-control font-monospace" value={createdKey.key} readOnly
                       onFocus={(e) => e.target.select()} />
                <button className="btn btn-secondary" type="button" onClick={copyKey}>
                  {copiedToClipboard ? <span><i className="fa fa-check"/> Copied</span> : "Copy"}
                </button>
              </div>

              <div className="form-text">
                {createdKey.expires_at
                    ? <span>Expires {moment(createdKey.expires_at).format()}.</span>
                    : <span>This key does not expire.</span>}
              </div>

              <button className="btn btn-sm btn-outline-secondary mt-2" type="button" onClick={dismissKey}>
                I have stored the key
              </button>
            </div>
            :
        <form>
          <div className="mb-3">
            <label htmlFor="apiKeyName" className="form-label">Name</label>
            <input type="text" className="form-control" id="apiKeyName" value={name}
                   onChange={(e) => setName(e.target.value)} maxLength={128} />
            <div className="form-text">
              A name that helps you recognize where this key is used, for example &quot;Nzyme CLI&quot;.
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="apiKeyExpiry" className="form-label">Expiration</label>
            <select className="form-select" id="apiKeyExpiry" value={expiryDays}
                    onChange={(e) => setExpiryDays(e.target.value)}>
              <option value="30">30 days</option>
              <option value="90">90 days</option>
              <option value="180">180 days</option>
              <option value="365">1 year</option>
              <option value="">Never</option>
            </select>
            <div className="form-text">
              The key stops working after this period. You can delete a key at any time.
            </div>
          </div>

          <button className="btn btn-sm btn-success" onClick={submit} disabled={!formIsReady() || formSubmitting}>
            {formSubmitting ? "Please wait ..." : "Create API Key"}
          </button>
        </form>
        }
      </React.Fragment>
  )

}
