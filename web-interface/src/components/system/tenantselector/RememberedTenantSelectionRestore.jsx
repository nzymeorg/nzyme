import React, {useEffect} from "react";
import InitializingPage from "../../misc/InitializingPage";
import AuthenticationManagementService from "../../../services/AuthenticationManagementService";
import {forgetTenantSelection} from "./rememberedTenantSelection";

const authenticationManagementService = new AuthenticationManagementService();

export default function RememberedTenantSelectionRestore({user, selection, darkModeEnabled, onRestored, onSkipped}) {

  useEffect(() => {
    const controller = new AbortController();

    authenticationManagementService.findTenantOfOrganization(selection.organization, selection.tenant, () => {
      onRestored(selection.organization, selection.tenant);
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
