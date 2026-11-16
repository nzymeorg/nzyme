import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import {apiErrorMessage} from "../../../../util/Tools";
import {TAG_NAME_MAX_LENGTH, cleanTagName, removeTagQuestion} from "./AssetTagHelpers";

const assetTagsService = new AssetTagsService();

const NEW_TAG = "__new__";

export default function AssetTagsCard({asset, organizationId, tenantId, revision, onRefresh, canEdit}) {

  const [tags, setTags] = useState(null);
  const [allTags, setAllTags] = useState(null);
  const [localRevision, setLocalRevision] = useState(new Date());

  const [selected, setSelected] = useState("");
  const [newName, setNewName] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    assetTagsService.findTagsOfAsset(asset.uuid, organizationId, tenantId, setTags);
    assetTagsService.findAllTagsUnpaged(organizationId, tenantId, setAllTags);
  }, [asset.uuid, organizationId, tenantId, revision, localRevision]);

  const title = (
    <React.Fragment>
      <CardTitleWithControls title="Tags" refreshAction={onRefresh} />
      <p><Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX}>Manage tags</Link></p>
    </React.Fragment>
  );

  if (!tags || !allTags) {
    return <React.Fragment>{title}<LoadingSpinner /></React.Fragment>
  }

  const available = allTags.filter((t) => !tags.some((own) => own.uuid === t.uuid));

  const onRemove = (e, tag) => {
    e.preventDefault();

    if (!confirm(removeTagQuestion(tag))) {
      return;
    }

    assetTagsService.unassignTag(tag.uuid, asset.uuid, organizationId, tenantId, () => {
      toast.success("Tag removed.");
      setLocalRevision(new Date());
    }, (error) => {
      toast.error(apiErrorMessage(error, "Could not remove the tag."));
    });
  }

  const formIsReady = () => {
    if (isSubmitting) {
      return false;
    }

    return selected === NEW_TAG ? newName.trim().length > 0 : selected !== "";
  }

  const onAdd = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    const assign = (tagUuid) => {
      assetTagsService.assignTag(tagUuid, asset.uuid, organizationId, tenantId, () => {
        toast.success("Tag added.");
        setIsSubmitting(false);
        setSelected("");
        setNewName("");
        setLocalRevision(new Date());
      }, (error) => {
        setIsSubmitting(false);
        setErrorMessage(apiErrorMessage(error, "Could not add the tag."));
      });
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    if (selected !== NEW_TAG) {
      assign(selected);
      return;
    }

    const existing = allTags.find((t) => t.name === newName.trim());
    if (existing) {
      assign(existing.uuid);
      return;
    }

    assetTagsService.createTag(organizationId, tenantId, newName.trim(), null, (tag) => assign(tag.uuid), (error) => {
      setIsSubmitting(false);
      setErrorMessage(apiErrorMessage(error, "Could not create the tag."));
    });
  }

  return (
    <React.Fragment>
      {title}

      <div className={canEdit ? "mb-3" : ""}>
        {tags.length === 0 ?
          <span className="text-muted">This asset has no tags.</span>
          : tags.map((tag) => {
            return (
              <span className="badge bg-secondary me-1" style={{fontSize: "0.9em"}} key={tag.uuid}
                    title={tag.description ? tag.description : undefined}>
                <i className="fa-solid fa-tag"></i> {tag.name}
                {canEdit ?
                  <a href="#" className="text-white ms-2" title="Remove" onClick={(e) => onRemove(e, tag)}>
                    <i className="fa-solid fa-xmark"></i>
                  </a> : null}
              </span>
            )
          })}
      </div>

      {canEdit ?
        <form onSubmit={onAdd}>
          <div className="input-group input-group-sm" style={{maxWidth: 520}}>
            <select className="form-select" aria-label="Tag to add" value={selected}
                    onChange={(e) => { setSelected(e.target.value); setErrorMessage(null); }}>
              <option value="">Add a tag ...</option>
              {available.map((t) => <option key={t.uuid} value={t.uuid}>{t.name}{t.description ? " (" + t.description + ")" : ""}</option>)}
              <option value={NEW_TAG}>New tag ...</option>
            </select>
            {selected === NEW_TAG ?
              <input type="text" className="form-control" aria-label="Name of the new tag" placeholder="Name, like db-servers"
                     autoComplete="off" maxLength={TAG_NAME_MAX_LENGTH}
                     value={newName} onChange={(e) => setNewName(cleanTagName(e.target.value))} /> : null}
            <button className="btn btn-outline-secondary" type="submit" disabled={!formIsReady()}>
              {isSubmitting ? "Please wait ..." : "Add"}
            </button>
          </div>

          {errorMessage ? <div className="text-danger mt-1">{errorMessage}</div> : null}
        </form> : null}
    </React.Fragment>
  )

}
