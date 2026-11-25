import React, {useState} from "react";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";
import FormSubmitErrorMessage from "../../../../../../misc/FormSubmitErrorMessage";

const authenticationManagementService = new AuthenticationManagementService();

const FEET_CONVERSION = 0.3048;

function UploadFloorPlanForm(props) {

  const organizationId = props.organizationId;
  const tenantId = props.tenantId;
  const locationId = props.locationId;
  const floorId = props.floorId;

  const hasExistingPlan = props.hasExistingPlan;
  const submitText = props.submitText;

  const [unit, setUnit] = useState("Feet");
  const [width, setWidth] = useState("")
  const [length, setLength] = useState("");

  const [isUploading, setIsUploading] = useState(false);

  const [errorMessage, setErrorMessage] = useState(null);
  const onSuccess = props.onSuccess;

  const [planFiles, setPlanFiles] = useState([]);

  const submit = () => {
    if (hasExistingPlan && !confirm("Replace floor plan? The existing plan for this floor will be replaced.")) {
      return;
    }

    // Convert unit if required.
    let widthMeters, lengthMeters;
    if (unit === "Feet") {
      widthMeters = width*FEET_CONVERSION;
      lengthMeters = length*FEET_CONVERSION;
    } else {
      widthMeters = width;
      lengthMeters = length;
    }

    const formData = new FormData();
    formData.append("plan", planFiles[0]);
    formData.append("width_meters", Math.round(widthMeters));
    formData.append("length_meters", Math.round(lengthMeters));

    setIsUploading(true);
    authenticationManagementService.uploadFloorPlan(organizationId, tenantId, locationId, floorId, formData,
        () => {
          setIsUploading(false);
          onSuccess();
        },
        (error) => {
          setIsUploading(false);
          if (error.response && error.response.status === 400) {
            setErrorMessage(error.response.data.message);
          } else {
            setErrorMessage("Unexpected error. Please try again.");
          }
        })
  }

  const toggleUnit = (e) => {
    e.preventDefault();

    if (unit === "Feet") {
      setUnit("Meters");
    } else {
      setUnit("Feet");
    }
  }

  const formIsReady = () => {
    return planFiles.length > 0 && parseInt(width, 10) > 0 && parseInt(length, 10) > 0 && !isUploading
  }

  return (
      <React.Fragment>
        <div className="mb-3">
          <label htmlFor="plan" className="form-label">
            Floor Plan Image File (JPG or PNG, maximum file size 5MB, maximum 40 megapixels)
          </label>
          <input className="form-control form-control-sm" name="plan" id="plan" type="file"
                 onChange={(e) => setPlanFiles(e.target.files)}/>
        </div>

        <div className="row">
          <div className="col-6">
            <label htmlFor="width_meters" className="form-label">
              Width (X Axis)
            </label>

            <div className="input-group mb-3">
              <input className="form-control" name="width_meters" id="width_meters" type="number"
                     min={1} onChange={(e) => setWidth(e.target.value)}/>
              <button className="btn btn-outline-secondary" type="button" onClick={toggleUnit}>
                {unit} &nbsp;<small><i className="fa-solid fa-repeat"></i></small>
              </button>
            </div>
          </div>
          <div className="col-6">
            <label htmlFor="length_meters" className="form-label">
              Length (Y Axis)
            </label>
            <div className="input-group mb-3">
              <input className="form-control" name="length_meters" id="length_meters" type="number" min={1}
                     onChange={(e) => setLength(e.target.value)}/>
              <button className="btn btn-outline-secondary" type="button" onClick={toggleUnit}>
                {unit} &nbsp;<small><i className="fa-solid fa-repeat"></i></small>
              </button>
            </div>
          </div>
        </div>

        <p className="text-muted mt-0">
          Dimension values are converted to rounded meters internally.
        </p>

        <button className="btn btn-sm btn-primary" onClick={submit} disabled={!formIsReady()}>
          {isUploading ? "Please Wait ..." : submitText}
        </button>

        <FormSubmitErrorMessage message={errorMessage}/>
      </React.Fragment>
  )

}

export default UploadFloorPlanForm;