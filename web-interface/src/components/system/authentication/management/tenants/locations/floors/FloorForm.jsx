import React, {useState} from "react";
import FormSubmitErrorMessage from "../../../../../../misc/FormSubmitErrorMessage";

function FloorForm(props) {

  const onSubmit = props.onSubmit;
  const submitText = props.submitText;
  const [number, setNumber] = useState(props.number ? props.number : "");
  const [pathLossExponent, setPathLossExponent] = useState(props.pathLossExponent ? props.pathLossExponent : "3.0")
  const [name, setName] = useState(props.name ? props.name : "");

  const errorMessage = props.errorMessage;

  const updateValue = function(e, setter) {
    setter(e.target.value);
  }

  const formIsReady = function() {
    return number && pathLossExponent && parseFloat(pathLossExponent) > 0
  }

  const submit = function(e) {
    e.preventDefault();
    onSubmit(number, name, pathLossExponent);
  }

  return (
      <form>
        <div className="mb-3">
          <label htmlFor="number" className="form-label">Floor Number</label>
          <input type="number" className="form-control" id="number" aria-describedby="number" placeholder="0"
                 value={number} onChange={(e) => { updateValue(e, setNumber) }}/>
          <div className="form-text">
            The floor number. For example, ground level would be 0 and the first floor would be 1. Must be unique for
            this location. Does not have to be continuous across the location and does not have to start at 0. For
            example, you could only have floors 20 and 25 if you are leasing space in a larger building or only deploy
            WiFi taps on select floors.
          </div>
        </div>

        <div className="mb-3">
          <label htmlFor="name" className="form-label">Name <small>Optional</small></label>
          <input type="text" className="form-control" id="description" maxLength={128} placeholder="Ground Level"
                    value={name} onChange={(e) => { updateValue(e, setName) }}/>
          <div className="form-text">
            The name of this floor. Defaults to the floor number (like &quot;Floor 1&quot;) if not provided
          </div>
        </div>

        <div className="mb-3">
          <label htmlFor="pathLossExponent" className="form-label">Path Loss Exponent</label>
          <input type="number" className="form-control" id="pathLossExponent" step="0.1"
                    value={pathLossExponent} onChange={(e) => { updateValue(e, setPathLossExponent) }}/>
          <div className="form-text">
            The path loss exponent quantifies how rapidly the signal strength diminishes with distance between taps
            on this floor and a signal source, increasing in environments with thicker walls and more obstacles to
            account for greater signal attenuation. The default value of <code>3.0</code> is a average value for North
            American residential or office buildings. In an ideal, obstruction-free environment (like outer space or an
            empty field), the path loss exponent is <code>2.0</code>. In areas with large obstacles or a lot of clutter,
            the path loss exponent can be even higher, often ranging from <code>3.0</code> to <code>5.0</code>.
          </div>
        </div>

        <button className="btn btn-sm btn-primary" onClick={submit} disabled={!formIsReady()}>
          {submitText}
        </button>

        <FormSubmitErrorMessage message={errorMessage}/>
      </form>
  )

}

export default FloorForm;