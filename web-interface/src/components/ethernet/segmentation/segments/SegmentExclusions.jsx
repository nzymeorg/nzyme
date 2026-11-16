import React, {useEffect, useState} from "react";
import moment from "moment";
import {toast} from "react-toastify";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {apiErrorMessage} from "../../../../util/Tools";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import {EXCLUSION_NOTE_MAX_LENGTH, exclusionDestination} from "../SegmentationFormat";

const segmentationService = new SegmentationService();

export default function SegmentExclusions({segment, organizationId, tenantId, canEdit}) {

  const [exclusions, setExclusions] = useState(null);
  const [revision, setRevision] = useState(0);

  const [destination, setDestination] = useState("");
  const [note, setNote] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    setExclusions(null);
    segmentationService.findSegmentExclusions(segment.uuid, organizationId, tenantId, setExclusions);
  }, [segment.uuid, organizationId, tenantId, revision]);

  const onSubmit = (e) => {
    e.preventDefault();

    if (!confirm("Exclude " + destination.trim() + "? Flows of unanswered attempts to it that are stored already are deleted.")) {
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    segmentationService.createSegmentExclusion(segment.uuid, organizationId, tenantId, destination.trim(), note.trim(), () => {
      setIsSubmitting(false);
      setDestination("");
      setNote("");
      toast.success("Destination excluded.");
      setRevision((r) => r + 1);
    }, (error) => {
      setIsSubmitting(false);
      setErrorMessage(apiErrorMessage(error, "Could not exclude the destination."));
    });
  }

  const onDelete = (e, exclusion) => {
    e.preventDefault();

    if (!confirm("Stop excluding " + exclusionDestination(exclusion) + "? Attempts to it are recorded again from now on.")) {
      return;
    }

    segmentationService.deleteSegmentExclusion(segment.uuid, exclusion.uuid, organizationId, tenantId, () => {
      toast.success("Exclusion deleted.");
      setRevision((r) => r + 1);
    }, (error) => {
      toast.error(apiErrorMessage(error, "Could not delete the exclusion."));
    });
  }

  if (!exclusions) {
    return <LoadingSpinner />
  }

  // Not passed on to a user who has no access to every tap that records the segment.
  const hidden = (exclusion) => exclusion.match_count === null || exclusion.match_count === undefined;
  const notAvailable = <span className="text-muted" title="Requires access to all taps that record the segment">n/a</span>;

  const matches = (exclusion) => (hidden(exclusion) ? notAvailable : formatCount(exclusion.match_count));

  const lastMatched = (exclusion) => {
    if (hidden(exclusion)) {
      return notAvailable;
    }

    return exclusion.last_matched_at
      ? <span title={moment(exclusion.last_matched_at).format()}>{moment(exclusion.last_matched_at).fromNow()}</span>
      : <span className="text-muted">Never</span>;
  }

  const full = exclusions.exclusions.length >= exclusions.limit;
  const canChange = canEdit && exclusions.all_taps_accessible;

  return (
    <React.Fragment>
      {exclusions.exclusions.length === 0 ?
        <div className="alert alert-info">No destinations are excluded.</div>
        : <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Destination</th>
            <th>Note</th>
            <th title="Sessions that were not recorded because of the exclusion">Matches</th>
            <th>Last Matched</th>
            <th>Created</th>
            {canChange ? <th>&nbsp;</th> : null}
          </tr>
          </thead>
          <tbody>
          {exclusions.exclusions.map((exclusion) => (
            <tr key={exclusion.uuid}>
              <td><span className="machine-data">{exclusionDestination(exclusion)}</span></td>
              <td>{exclusion.note}</td>
              <td>{matches(exclusion)}</td>
              <td>{lastMatched(exclusion)}</td>
              <td title={moment(exclusion.created_at).format()}>{moment(exclusion.created_at).fromNow()}</td>
              {canChange ?
                <td className="text-end">
                  <a href="#" onClick={(e) => onDelete(e, exclusion)}>Delete</a>
                </td> : null}
            </tr>
          ))}
          </tbody>
        </table>}

      {canChange && full ?
        <div className="alert alert-warning mb-0">
          Limited to {exclusions.limit} excluded destinations per segment.
        </div> : null}

      {canChange && !full ?
        <form onSubmit={onSubmit}>
          <div className="row g-2">
            <div className="col-md-3">
              <label htmlFor="exclusion-destination" className="form-label">Destination</label>
              <input type="text" className="form-control" id="exclusion-destination" value={destination}
                     placeholder="172.16.0.157 or 172.16.0.0/24"
                     onChange={(e) => setDestination(e.target.value)} />
            </div>
            <div className="col-md-7">
              <label htmlFor="exclusion-note" className="form-label">Note</label>
              <input type="text" className="form-control" id="exclusion-note" value={note}
                     maxLength={EXCLUSION_NOTE_MAX_LENGTH}
                     placeholder="Why attempts to the destination can be ignored"
                     onChange={(e) => setNote(e.target.value)} />
            </div>
            <div className="col-md-2 d-flex align-items-end">
              <button type="submit" className="btn btn-primary w-100"
                      disabled={isSubmitting || destination.trim() === "" || note.trim() === ""}>
                {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;Excluding ...</span>
                  : "Exclude Destination"}
              </button>
            </div>
          </div>

          {errorMessage ? <div className="alert alert-danger mt-3 mb-0">{errorMessage}</div> : null}
        </form> : null}
    </React.Fragment>
  )

}
