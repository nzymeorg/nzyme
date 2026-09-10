import React, {useEffect} from "react";

function TenantSelector(props) {

  const tenants = props.tenants;
  const tenant = props.tenant;
  const setTenant = props.setTenant;

  useEffect(() => {
    if (tenants) {
      if (tenants.tenants.length === 1) {
        // Automatically select tenant if there is only one.
        setTenant(tenants.tenants[0].id)
      }
    }
  }, [tenants]);

  if (!tenants) {
    return (
        <select className="form-select mb-3" disabled={true}>
          <option>Select an Organization first.</option>
        </select>
    )
  }

  return (
      <select className="form-select mb-3"
              value={tenant}
              onChange={(e) => setTenant(e.target.value)}>
        <option value="">Select a Tenant</option>
        {tenants.tenants.map(function(tenant, i) {
          return (
              <option value={tenant.id} key={"tenant-" + i}>
                {tenant.name}
              </option>
          )
        })}
      </select>
  )

}

export default TenantSelector;