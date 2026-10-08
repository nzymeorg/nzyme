import React, {useContext, useState} from "react";
import {createPortal} from "react-dom";
import GlobalTenantSelectorForm from "./GlobalTenantSelectorForm";

import Store from "../../../util/Store";
import {AppContext, TapContext} from "../../../App";

export default function GlobalTenantSelectorDialog() {

  const app = useContext(AppContext);
  const tapContext = useContext(TapContext);

  const [selectedOrganization, setSelectedOrganization] = useState(null);
  const [selectedTenant, setSelectedTenant] = useState(false);

  const onSelectionMade = (organization, tenant) => {
    setSelectedOrganization(organization);
    setSelectedTenant(tenant);
  }

  const save = () => {
    Store.set("selected_organization", selectedOrganization);
    Store.set("selected_tenant", selectedTenant);

    // Reset taps as well.
    Store.delete("selected_taps");
    tapContext.set([]);

    app.setRevision(new Date());
  }

  // IMPORTANT: The forced selector that shows when no org/tenant selection has been made has its own callbacks.

  // Rendered into <body> via portal: the sticky navbar creates a stacking context that would otherwise trap
  // the modal underneath the Bootstrap backdrop.
  return createPortal(
    <div className="modal" id="global-tenant-selector">
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h1 className="modal-title fs-5" id="exampleModalLabel">Tenant Selector</h1>
            <button type="button" className="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div className="modal-body">
            <p className="text-muted">
              Select the tenant you want to use the web interface as. This does not affect your user&apos;s privileges
              but is required to access tenant-specific dialogs and pages.
            </p>

            <GlobalTenantSelectorForm onSelectionMade={onSelectionMade} />
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" data-bs-dismiss="modal">Close</button>

            {selectedOrganization && selectedTenant ?
              <button type="button" className="btn btn-primary" data-bs-dismiss="modal" onClick={save}>Select Tenant</button>
            : <button type="button" className="btn btn-primary" disabled={true}>Select Tenant</button> }
          </div>
        </div>
      </div>
    </div>,
    document.body
  )

}