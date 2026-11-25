import React, {useEffect, useState} from "react";
import LocationsService from "../../../services/LocationsService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {toast} from "react-toastify";

const locationsService = new LocationsService();

export default function FloorSelector({locationId, floor, onFloorSelected, organizationId: ofOrganization, tenantId: ofTenant}) {

  const [selectedOrganization, selectedTenant] = useSelectedTenant();
  const organizationId = ofOrganization ? ofOrganization : selectedOrganization;
  const tenantId = ofTenant ? ofTenant : selectedTenant;

  const [location, setLocation] = useState(null);

  const [selected, setSelected] = useState(floor ? floor : "");

  useEffect(() => {
    let active = true;

    if (locationId) {
      setLocation(null);
      locationsService.findOne(locationId, organizationId, tenantId, (response) => {
        if (active) {
          setLocation(response);
        }
      }, (error) => {
        if (!active) {
          return;
        }

        // Specific error handling because we ignore 404's. It means the location was deleted, which is fine.
        if (error.response && error.response.status === 404) {
          setLocation(null);
        } else {
          toast.error("Could not fetch locations.")
        }
      })
    }

    return () => {
      active = false;
    }
  }, [organizationId, tenantId, locationId]);

  // No location selected yet. No floors to select.
  if (!locationId) {
    return null;
  }

  // No loading spinner for this one.
  if (location === null) {
    return null;
  }

  // Show nothing if there are no floors. Keeps it less busy.
  if (location.floors.length === 0) {
    return null;
  }

  return (
    <div className="mt-3">
      <label htmlFor="floor-select" className="form-label">Floor <small>Optional</small></label>

      <select className="form-select"
              id="floor-select"
              value={selected}
              onChange={(e) => {
                e.preventDefault();
                onFloorSelected(e.target.value);
                setSelected(e.target.value);
              }}>
        <option value="">No floor selected</option>
        {location.floors.map((floor, i) => {
          return <option value={floor.uuid} key={i}>{floor.name}</option>
        })}
      </select>
    </div>
  )

}