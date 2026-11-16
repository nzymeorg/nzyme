import React, {useState} from "react";
import FormSubmitErrorMessage from "../../../misc/FormSubmitErrorMessage";
import {apiErrorMessage} from "../../../../util/Tools";
import {TAG_DESCRIPTION_MAX_LENGTH, TAG_NAME_MAX_LENGTH, cleanTagName} from "./AssetTagHelpers";

export default function AssetTagForm({name = "", description = "", submitText, onSubmit}) {

  const [newName, setNewName] = useState(name);
  const [newDescription, setNewDescription] = useState(description);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const formIsReady = () => {
    return newName.trim().length > 0 && !isSubmitting;
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    onSubmit(newName.trim(), newDescription.trim() === "" ? null : newDescription.trim(), (error) => {
      setIsSubmitting(false);
      setSubmitError(apiErrorMessage(error, "Could not save the tag."));
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="mb-3">
        <label htmlFor="tag-name" className="form-label">Name</label>
        <input type="text" className="form-control" id="tag-name" autoComplete="off"
               maxLength={TAG_NAME_MAX_LENGTH}
               value={newName} onChange={(e) => setNewName(cleanTagName(e.target.value))} />
        <div className="form-text">
          Up to {TAG_NAME_MAX_LENGTH} characters: letters, digits, dashes, underscores and dots.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="tag-description" className="form-label">Description <small>Optional</small></label>
        <input type="text" className="form-control" id="tag-description" autoComplete="off"
               maxLength={TAG_DESCRIPTION_MAX_LENGTH}
               value={newDescription} onChange={(e) => setNewDescription(e.target.value)} />
        <div className="form-text">Up to {TAG_DESCRIPTION_MAX_LENGTH} characters.</div>
      </div>

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>

      <FormSubmitErrorMessage message={submitError} />
    </form>
  )

}
