import React, {useEffect, useState} from "react";
import WithPermission from "../../misc/WithPermission";
import ApiRoutes from "../../../util/ApiRoutes";
import {Navigate, useParams} from "react-router-dom";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {toast} from "react-toastify";
import moment from "moment";
import ContextNotes from "../ContextNotes";
import TransparentIpAddressTable from "../../shared/context/transparent/TransparentIpAddressTable";
import TransparentHostnamesTable from "../../shared/context/transparent/TransparentHostnamesTable";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const contextService = new ContextService();

function MacAddressContextDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);

  const [deleted, setDeleted] = useState(false);
  const [deleting, setDeleting] = useState(false);

  usePageTitle(context ? `MAC Address Context: ${context.mac_address}` : "MAC Address Context Details");

  useEffect(() => {
    contextService.findMacAddressContextByUuid(uuid, organizationId, tenantId, setContext);
  }, [uuid, organizationId, tenantId]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete MAC address context?")) {
      return;
    }

    setDeleting(true);
    contextService.deleteMacAddressContext(uuid, organizationId, tenantId, () => {
      toast.success('MAC address context deleted.');
      setDeleted(true);
    });
  }

  if (deleted) {
    return <Navigate to={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX} />
  }

  if (!context) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">Context</li>
                <li className="breadcrumb-item"><a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}>MAC Addresses</a></li>
                <li className="breadcrumb-item active">{context.mac_address}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Context of MAC Address &quot;{context.mac_address}&quot;{' '}

              {context.mac_address_is_randomized ?
                  <i className="fa-solid fa-triangle-exclamation text-danger cursor-help"
                     title="This is a randomized MAC address."/>
                  : null}
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <WithPermission permission="mac_context_manage">
                <button className="btn btn-danger" onClick={onDelete} disabled={deleting}>{deleting ? "Please wait ..." : "Delete"}</button>&nbsp;
                <a className="btn btn-secondary"
                   href={ApiRoutes.CONTEXT.MAC_ADDRESSES.EDIT(context.uuid)}>
                  Edit
                </a>&nbsp;
              </WithPermission>
              <a className="btn btn-primary" href={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}>Back</a>
            </span>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-8">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>

                <dl className="mb-0">
                  <dt>Name</dt>
                  <dd>{context.name ? <span className="context-name context-name-asset">{context.name}</span> :
                      <span className="text-muted">None</span>}</dd>
                  <dt>Description</dt>
                  <dd>{context.description ? context.description : <span className="text-muted">None</span>}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>Metadata</h3>

                <dl className="mb-0">
                  <dt>Created at</dt>
                  <dd title={context.created_at}>{moment(context.created_at).fromNow()}</dd>
                  <dt>Updated at</dt>
                  <dd title={context.updated_at}>{moment(context.updated_at).fromNow()}</dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>IP Addresses</h3>

                <TransparentIpAddressTable addresses={context.transparent_ip_addresses} />
              </div>
            </div>
          </div>

          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <h3>Hostnames</h3>

                <TransparentHostnamesTable hostnames={context.transparent_hostnames} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-8">
            <div className="card">
              <div className="card-body">
                <h3>Notes</h3>

                <ContextNotes notes={context.notes}/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MacAddressContextDetailsPage;