import React, {useState} from "react";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {apiErrorMessage} from "../../../../util/Tools";
import {EXCLUSION_DESCRIPTION, EXCLUSION_NOTE_MAX_LENGTH} from "../SegmentationFormat";

const segmentationService = new SegmentationService();

export default function ExcludeDestinationDialog({organizationId, tenantId, flow, onClose, onComplete}) {

  const [note, setNote] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const destination = flow.destination.address.address;

  const onSubmit = (e) => {
    e.preventDefault();

    setIsSubmitting(true);
    setErrorMessage(null);

    segmentationService.createSegmentExclusion(flow.source.segment_uuid, organizationId, tenantId, destination,
      note.trim(), () => {
        setIsSubmitting(false);
        onComplete();
      }, (error) => {
        setIsSubmitting(false);
        setErrorMessage(apiErrorMessage(error, "Could not exclude the destination."));
      });
  }

  return (
    <React.Fragment>
      <div className="modal-backdrop fade show"></div>
      <div className="modal fade show" style={{display: "block"}}>
        <div className="modal-dialog modal-lg modal-dialog-centered">
          <form className="modal-content" onSubmit={onSubmit}>
            <div className="modal-header">
              <h1 className="modal-title fs-5">Exclude Destination</h1>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <p>{EXCLUSION_DESCRIPTION} Flows of such attempts that are stored already are deleted.</p>

              <dl>
                <dt>Segment</dt>
                <dd>{flow.source.segment_name}</dd>
                <dt>Destination</dt>
                <dd className="machine-data">{destination}</dd>
              </dl>

              <label htmlFor="exclude-destination-note" className="form-label">Note</label>
              <input type="text" className="form-control" id="exclude-destination-note" value={note}
                     maxLength={EXCLUSION_NOTE_MAX_LENGTH} autoFocus={true}
                     placeholder="Why attempts to the destination can be ignored"
                     onChange={(e) => setNote(e.target.value)} />

              {errorMessage ? <div className="alert alert-danger mt-3 mb-0">{errorMessage}</div> : null}
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>Close</button>
              <button type="submit" className="btn btn-primary" disabled={isSubmitting || note.trim() === ""}>
                {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> &nbsp;Excluding ...</span>
                  : "Exclude Destination"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </React.Fragment>
  )

}
