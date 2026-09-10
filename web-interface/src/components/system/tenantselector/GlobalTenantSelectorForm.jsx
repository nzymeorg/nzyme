import React, {useContext, useEffect, useState} from "react";
import {UserContext} from "../../../App";
import AuthenticationManagementService from "../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import OrganizationSelector from "../../shared/OrganizationSelector";
import RefreshGears from "../../misc/RefreshGears";
import TenantSelector from "../../shared/TenantSelector";
import Store from "../../../util/Store";

const authenticationManagementService = new AuthenticationManagementService();

export default function GlobalTenantSelectorForm({onSelectionMade, onAutoSelect}) {

  const user = useContext(UserContext);

  const selectedOrganization = Store.get("selected_organization");
  const selectedTenant = Store.get("selected_tenant");

  const [organization, setOrganization] = useState(selectedOrganization ? selectedOrganization : "");
  const [tenant, setTenant] = useState(selectedTenant ? selectedTenant : "");

  const [organizations, setOrganizations] = useState(null);
  const [tenants, setTenants] = useState(null);

  const [userOrganization, setUserOrganization] = useState(null)
  const [userTenant, setUserTenant] = useState(null);

  const [tenantsLoading, setTenantsLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setLoaded(false);

    if (user.is_superadmin) {
      setUserOrganization(null);
      setUserTenant(null);

      authenticationManagementService.findAllOrganizations(function(response) {
        setOrganizations(response);

        const orgList = asList(response, "organizations");
        if (orgList.length === 1) {
          // Only one organization. Look at its tenants before deciding.
          const onlyOrganization = orgList[0];

          authenticationManagementService.findAllTenantsOfOrganization(onlyOrganization.id, function(tenantResponse) {
            const tenantList = asList(tenantResponse, "tenants");

            if (onAutoSelect && tenantList.length === 1) {
              // Exactly one organization and one tenant. Skip selection entirely.
              onAutoSelect(onlyOrganization.id, tenantList[0].id);
            } else {
              // Pre-select the only organization, let the user pick a tenant.
              setOrganization(onlyOrganization.id);
              setLoaded(true);
            }
          }, 250, 0, function() {});
        } else {
          setLoaded(true);
        }
      }, 250, 0, function() {});
      return
    }

    if (user.is_orgadmin) {
      setUserOrganization(user.organization_id);
      setUserTenant(null);

      authenticationManagementService.findAllTenantsOfOrganization(user.organization_id, function(response) {
        setTenants(response);

        const tenantList = asList(response, "tenants");
        if (onAutoSelect && tenantList.length === 1) {
          // Only one tenant in this organization. Skip selection entirely.
          onAutoSelect(user.organization_id, tenantList[0].id);
        } else {
          setLoaded(true);
        }
      }, 250, 0, function() {});
      return
    }

    // Tenant user.
    onSelectionMade(user.organization_id, user.tenant_id);
    setUserOrganization(user.organization_id);
    setUserTenant(user.tenant_id);

    setLoaded(true);
  }, []);

  useEffect(() => {
    if (loaded) {
      setTenants(null);
      setTenant(null);
    }

    if (organization) {
      setTenantsLoading(true);
      authenticationManagementService.findAllTenantsOfOrganization(organization, setTenants,
        250, 0, function () {
          setTenantsLoading(false);
        });
    }
  }, [organization]);

  useEffect(() => {
    if (tenant) {
      if (!organization) {
        // Selection made as org admin. Use user org.
        onSelectionMade(userOrganization, tenant);
      } else {
        // Superadmin made selection.
        onSelectionMade(organization, tenant);
      }

    } else {
      if (loaded) {
        onSelectionMade(null, null);
      }
    }
  }, [tenant]);

  const asList = (data, key) => {
    if (!data) {
      return [];
    }
    if (Array.isArray(data)) {
      return data;
    }
    return data[key] || [];
  };

  if (!loaded) {
    return <LoadingSpinner />
  }

  if (userOrganization === null && userTenant == null) {
    // Superadmin. Let user select any organization and any tenant.
    return (
      <React.Fragment>
        <label className="form-label">Organization</label>

        <OrganizationSelector organization={organization}
                              setOrganization={setOrganization}
                              organizations={organizations} />

        <label className="form-label">
          Tenant {tenantsLoading ? <RefreshGears /> : null}
        </label>

        <TenantSelector tenant={tenant}
                        setTenant={setTenant}
                        tenants={tenants} />
      </React.Fragment>
    )
  }

  if (userOrganization && userTenant == null) {
    // Org admin. Let user select any tenant of their organization.
    return (
      <React.Fragment>
        <label className="form-label">Tenant</label>

        <TenantSelector tenant={tenant}
                        setTenant={setTenant}
                        tenants={tenants} />
      </React.Fragment>
    )
  }

  // Tenant user. Nothing to select.
  return null;

}