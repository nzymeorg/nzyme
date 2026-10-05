import React, {useContext, useState} from "react";
import ContextService from "../../../services/ContextService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {toast} from "react-toastify";
import {formatContextName, userHasPermission} from "../../../util/Tools";
import {UserContext} from "../../../App";

const contextService = new ContextService();

export default function AssetDetailsAssetName({asset, setRevision}) {

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);

  const SAVE_BUTTON_TEXT = "Set Name";

  const [showForm, setShowForm] = useState(false);

  const [newName, setNewName] = useState(asset.name ? formatContextName(asset.name) : "")

  const [saveButtonText, setSaveButtonText] = useState(SAVE_BUTTON_TEXT);
  const [isSaving, setIsSaving] = useState(false);

  const toggleForm = (e) => {
    e.preventDefault();
    setShowForm(!showForm);
  }

  const onSubmit = (e) => {
    e.preventDefault();

    setIsSaving(true);
    setSaveButtonText("Please wait...");

    contextService.setMacAddressName(asset.mac.address, newName, organizationId, tenantId, () => {
      toast.success("Asset name updated.");
      setRevision(new Date());
    }, () => {
      toast.error("Could not update asset name.");
      setIsSaving(false);
      setSaveButtonText(SAVE_BUTTON_TEXT);
    })
  }

  const name = () => {
    if (asset.name) {
      return <span className="context-name context-name-asset">{asset.name}</span>
    } else {
      return <span className="text-muted">n/a</span>
    }
  }

  const button = () => {
    if (showForm || !userHasPermission(user, "mac_context_manage")) {
      return;
    }

    if (asset.name) {
      return <a href="#" onClick={toggleForm}>Change Name</a>
    } else {
      return <a href="#" onClick={toggleForm}>Set Name</a>
    }
  }

  const form = () => {
    if (!showForm) {
      return null;
    }

    return (
        <>
          <div className="input-group mt-2 mb-2">
            <input type="text" className="form-control" id="new-asset-name" maxLength={12}
                   value={newName} onChange={(e) => { setNewName(formatContextName(e.target.value)) }} />

            <button className="btn btn-sm btn-primary" type="button" disabled={isSaving} onClick={onSubmit}>
              {saveButtonText}
            </button>

            <button className="btn btn-sm btn-secondary" type="button" onClick={toggleForm} disabled={isSaving}>
              Cancel
            </button>
          </div>

          <p className="text-muted mb-0">Note that it can take a few seconds until the new name updates across the system.</p>
        </>
    )
  }

  return (
    <>
      {name()}{' '}{button()}

      {form()}
    </>
  )

}