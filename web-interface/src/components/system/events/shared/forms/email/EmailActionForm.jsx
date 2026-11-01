import React, {useState} from "react";
import EmailReceiverList from "./EmailReceiverList";

function EmailActionForm(props) {

  const action = props.action; // for edit
  const buttonText = props.buttonText;
  const onSubmit = props.onSubmit;

  const EMAILREGEX = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;

  const [name, setName] = useState(action && action.name ? action.name : "");
  const [description, setDescription] = useState(action && action.description ? action.description : "");
  const [subjectPrefix, setSubjectPrefix] = useState(action && action.configuration && action.configuration.subject_prefix
      ? action.configuration.subject_prefix : "[Nzyme]");
  const [newReceiverInput, setNewReceiverInput] = useState("");
  const [receivers, setReceivers] = useState(action && action.configuration && action.configuration.receivers
      ? action.configuration.receivers : []);

  const [submitText, setSubmitText] = useState(buttonText);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = function() {
    setIsSubmitting(true);
    setSubmitText("Submitting ... Please Wait")

    onSubmit(name, description, subjectPrefix, receivers);
  }

  const formReady = function() {
    return !isSubmitting
        && name && name !== ""
        && description && description !== ""
        && subjectPrefix && subjectPrefix !== ""
  }

  const addReceiverReady = function() {
    if (!newReceiverInput || newReceiverInput === "") {
      return false;
    }

    return !(newReceiverInput && !EMAILREGEX.test(newReceiverInput));
  }

  const addReceiver = function() {
    const arr = [...receivers];
    arr.push(newReceiverInput);
    setReceivers(arr);
    setNewReceiverInput("");
  }

  const removeReceiver = function(e, receiver) {
    e.preventDefault();

    const arr = [...receivers];
    const idx = arr.indexOf(receiver);
    arr.splice(idx, 1);
    setReceivers(arr);
  }

  return (
      <form className="mt-3">
        <div className="mb-3">
          <label htmlFor="name" className="form-label">Action Name</label>
          <input type="text"
                 className="form-control"
                 id="name"
                 value={name}
                 onChange={(e) => setName(e.target.value)} />
          <div className="form-text">
            A short description of this action to help others quickly identify it.
          </div>
        </div>

        <div className="mb-3">
          <label htmlFor="description" className="form-label">Description</label>
          <textarea className="form-control" id="description" rows="3"
                    value={description} onChange={(e) => setDescription(e.target.value)} />
          <div className="form-text">
            A short description of this action to help others understand what it does.
          </div>
        </div>

        <div className="mb-3">
          <label htmlFor="subjectPrefix" className="form-label">Subject Prefix</label>
          <input type="text"
                 className="form-control"
                 id="subjectPrefix"
                 placeholder="[Nzyme]"
                 value={subjectPrefix}
                 onChange={(e) => setSubjectPrefix(e.target.value)} />
          <div className="form-text">
            The subject of emails is always automatically generated, but you can set a prefix here.
          </div>
        </div>

        <div className="mb-3">
          <h4>Receiver Email Addresses</h4>
          <EmailReceiverList receivers={receivers} onDelete={removeReceiver} />
        </div>

        <div className="mb-3">
          <label htmlFor="addReceiver" className="form-label">Add Receiver Email Address</label>
          <div className="input-group">
            <input type="text"
                   className="form-control"
                   id="addReceiver"
                   value={newReceiverInput}
                   onChange={(e) => setNewReceiverInput(e.target.value)} />
            <button className="btn btn-secondary" type="button" onClick={addReceiver} disabled={!addReceiverReady()}>
              Add
            </button>
          </div>
        </div>

        <button type="button"
                className="btn btn-primary"
                disabled={!formReady()}
                onClick={submit}>
          {submitText}
        </button>
      </form>
  )

}

export default EmailActionForm;