import React, {useEffect, useState} from "react";
import LocationsService from "../../../services/LocationsService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {toast} from "react-toastify";

const locationsService = new LocationsService();

export default function LocationSelector({location, onLocationSelected, helpText, hideWithoutLocations, organizationId: ofOrganization, tenantId: ofTenant}) {

  const [selectedOrganization, selectedTenant] = useSelectedTenant();
  const organizationId = ofOrganization ? ofOrganization : selectedOrganization;
  const tenantId = ofTenant ? ofTenant : selectedTenant;

  const [locations, setLocations] = useState(null);

  const [selected, setSelected] = useState(location ? location : "");

  useEffect(() => {
    setLocations(null);
    locationsService.findAll(organizationId, tenantId, setLocations)
  }, [organizationId, tenantId]);

  if (locations === null) {
    return <LoadingSpinner />
  }

  if (locations.length === 0) {
    return hideWithoutLocations ? null : <div className="alert alert-info mb-0">No locations configured.</div>
  }

  return (
    <>
      <label htmlFor="floor-select" className="form-label">Location <small>Optional</small></label>

      <select className="form-select"
              id="location-select"
              value={selected}
              onChange={(e) => {e.preventDefault(); onLocationSelected(e.target.value); setSelected(e.target.value);}}>
        <option value="">No location assigned</option>
        {locations.map((location, i) => {
          return <option value={location.id} key={i}>{location.name}</option>
        })}
      </select>

      {helpText ? <div className="form-text">{helpText}</div> : null}
    </>
  )

}