import React, {useState} from "react";
import numeral from "numeral";
import {toast} from "react-toastify";
import BeaconsService from "../../../services/ethernet/BeaconsService";
import {beaconClassificationToName} from "./BeaconClassification";

const beaconsService = new BeaconsService();

export default function BulkBeaconClassification({total, filters, timeRange, organizationId, tenantId, onChanged}) {

  const [open, setOpen] = useState(false);
  const [classification, setClassification] = useState("BENIGN");
  const [isSaving, setIsSaving] = useState(false);

  const hasFilters = filters && Object.keys(filters).length > 0;
  const count = numeral(total).format("0,0");
  const scope = hasFilters
    ? "all " + count + " beacons matching the current filters and time range"
    : "all " + count + " beacons in the selected time range";

  const apply = (e) => {
    e.preventDefault();

    if (!confirm("Really classify " + scope + " as \"" + beaconClassificationToName(classification) + "\"? " +
        "This also affects beacons on other pages of the list.")) {
      return;
    }

    setIsSaving(true);
    beaconsService.classifyBeacons(organizationId, tenantId, filters, timeRange, classification, (response) => {
      setIsSaving(false);
      setOpen(false);
      toast.success(numeral(response.data.updated_count).format("0,0") + " beacons classified as \""
        + beaconClassificationToName(classification) + "\".");
      if (onChanged) {
        onChanged();
      }
    }, () => {
      setIsSaving(false);
      toast.error("Could not classify beacons.");
    });
  }

  if (!total || total === 0) {
    return null;
  }

  if (!open) {
    return (
      <button className="btn btn-sm btn-secondary"
              type="button"
              title={"Set the classification of " + scope + ", including those on other pages."}
              onClick={() => setOpen(true)}>
        Classify {hasFilters ? "all " + count + " Matching Beacons" : "all " + count + " Beacons"}
      </button>
    )
  }

  return (
    <div className="d-flex align-items-center justify-content-end">
      <span className="me-2">Classify {scope} as</span>

      <select className="form-select form-select-sm"
              style={{width: "auto"}}
              value={classification}
              disabled={isSaving}
              onChange={(e) => setClassification(e.target.value)}>
        <option value="UNKNOWN">Unknown</option>
        <option value="BENIGN">Benign</option>
        <option value="SUSPICIOUS">Suspicious</option>
        <option value="MALICIOUS">Malicious</option>
      </select>

      <button className="btn btn-sm btn-primary ms-2" type="button" onClick={apply} disabled={isSaving}>
        {isSaving ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Applying ...</span> : "Apply"}
      </button>

      <a href="#" className="ms-2" onClick={(e) => { e.preventDefault(); setOpen(false); }}>Cancel</a>
    </div>
  )

}
