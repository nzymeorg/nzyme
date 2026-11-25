import React, {useEffect, useState} from "react";
import LatLonMap from "../../../../../shared/LatLonMap";
import LatitudeLongitude from "../../../../../shared/LatitudeLongitude";

function LocationForm(props) {

  const onSubmit = props.onSubmit;
  const submitText = props.submitText;
  const [name, setName] = useState(props.name ? props.name : "");
  const [description, setDescription] = useState(props.description ? props.description : "");
  const defaultMapZoomLevel = props.defaultMapZoomLevel;
  const [longitude, setLongitude] = useState(props.longitude ? props.longitude : null);
  const [latitude, setLatitude] = useState(props.latitude ? props.latitude : null);

  const icon = L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/leaflet/icon-location.png',
    iconSize: [40, 52],
    iconAnchor: [20, 52],
    tooltipAnchor: [0, -52]
  });

  const updateValue = function(e, setter) {
    setter(e.target.value);
  }

  const formIsReady = function() {
    return name && name.trim().length > 0
  }

  const submit = function(e) {
    e.preventDefault();
    onSubmit(name, description, latitude, longitude);
  }

  return (
      <form>
        <div className="mb-3">
          <label htmlFor="name" className="form-label">Name</label>
          <input type="text" className="form-control" id="name" maxLength={128} aria-describedby="name"
                 value={name} onChange={(e) => { updateValue(e, setName) }} />
          <div className="form-text">The name of the location.</div>
        </div>

        <div className="mb-3">
          <label htmlFor="description" className="form-label">Description <small>Optional</small></label>
          <textarea className="form-control" id="description" maxLength={10000} rows="3"
                    value={description} onChange={(e) => { updateValue(e, setDescription) }} />
          <div className="form-text">A short description of the location.</div>
        </div>

        <div className="mb-3">
          <div className="mb-3 mt-4">
            <div className="mb-3">
              <div className="mt-3">
                <h3>Coordinates <small>(Optional)</small></h3>

                <p className="text-muted">
                  Optionally, you can define a latitude/longitude position for this location.
                </p>

                <LatLonMap latitude={latitude}
                           longitude={longitude}
                           containerHeight={300}
                           editMode={true}
                           icon={icon}
                           setLatitude={setLatitude}
                           setLongitude={setLongitude}
                           defaultZoomLevel={defaultMapZoomLevel} />

                <div className="mt-2">
                  <strong>Selected Location:</strong> <LatitudeLongitude latitude={latitude} longitude={longitude} skipAccuracy={true} />{' '}
                  (Please click on the map to place the location.)
                </div>
              </div>
            </div>
          </div>
        </div>

        <button className="btn btn-sm btn-primary" onClick={submit} disabled={!formIsReady()}>
          {submitText}
        </button>
      </form>
  )


}

export default LocationForm;