import React, {useEffect} from "react";
import InitializingPage from "../../misc/InitializingPage";
import AuthenticationManagementService from "../../../services/AuthenticationManagementService";
import {forgetTenantSelection} from "./rememberedTenantSelection";

const authenticationManagementService = new AuthenticationManagementService();

export default function RememberedTenantSelectionRestore({user, selection, darkModeEnabled, onRestored, onSkipped}) {

  useEffect(() => {
    const controller = new AbortController();

    authenticationManagementService.findTenantOfOrganization(selection.organization, selection.tenant, (tenant) => {
      // The node does not compare the tenant to the organization of the request.
      if (tenant.organization_id === selection.organization) {
        onRestored(selection.organization, selection.tenant);
      } else {
        forgetTenantSelection(user);
        onSkipped();
      }
    }, (error) => {
      if (error.response && error.response.status === 404) {
        forgetTenantSelection(user);
      }

      onSkipped();
    }, controller.signal);

    return () => controller.abort();
  }, []);

  return <InitializingPage darkModeEnabled={darkModeEnabled} />

}
