import React, {useState} from "react";
import MonitoredFingerprintsTable from "./MonitoredFingerprintsTable";
import Dot11Service from "../../../services/Dot11Service";
import {toast} from "react-toastify";
import RefreshGears from "../../misc/RefreshGears";

const dot11Service = new Dot11Service();

function MonitoredBSSIDFingerprintsModal(props) {
  const bssid = props.bssid;
  const bumpRevision = props.bumpRevision;
  const parentIsLoading = props.parentIsLoading;

  const [newFingerprint, setNewFingerprint] = useState("");
  const [formSubmitting, setFormSubmitting] = useState(false);

  const formReady = function() {
    const existingFingerprints = bssid.fingerprints.map(function(fp) {
      return fp.fingerprint;
    });

    return newFingerprint.trim().length === 64 && !existingFingerprints.includes(newFingerprint.trim().toLowerCase())
  }

  const addFingerprint = function (fingerprint) {
    setFormSubmitting(true);

    dot11Service.createMonitoredBSSIDFingerprint(bssid.ssid_uuid, bssid.uuid, fingerprint.trim(), function () {
      bumpRevision();
      toast.success("Fingerprint added.");
      setFormSubmitting(false);
      setNewFingerprint("");
    }, function () {
      toast.error("Could not add fingerprint. Please check Nzyme log file.");
      setFormSubmitting(false);
    })
  }

  const deleteFingerprint = function (fingerprintUUID) {
    if (!confirm("Really delete fingerprint?")) {
      return;
    }

    dot11Service.deleteMonitoredBSSIDFingerprint(bssid.ssid_uuid, bssid.uuid, fingerprintUUID, function() {
      bumpRevision();
      toast.success("Fingerprint deleted.");
    })
  }

  return (
      <div className="modal configuration-dialog"
           id={"bssid-fingerprints-" + bssid.mac.address.replaceAll(":", "")}
           data-bs-keyboard="true" data-bs-backdrop="static" tabIndex="-1"
           aria-labelledby="staticBackdropLabel" aria-hidden="true">
        <div className="modal-dialog modal-lg">
          <div className="modal-content">
            <div className="modal-header">
              <h3 className="modal-title">
                Monitored Fingerprints of BSSID &quot;{bssid.mac.address}&quot; {parentIsLoading ? <RefreshGears /> : null}
              </h3>
              <button type="button" className="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>

            <div className="modal-body">
              <MonitoredFingerprintsTable fingerprints={bssid.fingerprints} onDelete={deleteFingerprint} />

              <div className="input-group mb-3">
                <input type="text"
                       className="form-control"
                       placeholder="e54f990fd67dcb754163ee9f663622d769caee9f864662101a8682506199573a"
                       value={newFingerprint}
                       onChange={(e) => setNewFingerprint(e.target.value)} />
                <button className="btn btn-secondary"
                        disabled={!formReady()}
                        onClick={() => { addFingerprint(newFingerprint) }}>
                  {formSubmitting ? "Please wait..." : "Add Fingerprint"}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
  )
}

export default MonitoredBSSIDFingerprintsModal;