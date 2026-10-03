import React, {useEffect, useState} from "react";
import WithPermission from "../../misc/WithPermission";
import ApiRoutes from "../../../util/ApiRoutes";
import {Navigate, useParams} from "react-router-dom";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {toast} from "react-toastify";
import moment from "moment";
import ContextNotes from "../ContextNotes";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import {cidrToRange} from "../../../util/Tools";

const contextService = new ContextService();

export default function NetworkContextDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);
  const [range, setRange] = useState({from: null, to: null});

  const [deleted, setDeleted] = useState(false);
  const [deleting, setDeleting] = useState(false);

  usePageTitle(context ? `Network Context: ${context.cidr}` : "Network Context Details");

  useEffect(() => {
    contextService.findNetworkContextByUuid(uuid, organizationId, tenantId, setContext);
  }, [uuid, organizationId, tenantId]);

  useEffect(() => {
    if (context) {
      setRange(cidrToRange(context.cidr));
    }
  }, [context])

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete network context?")) {
      return;
    }

    setDeleting(true);
    contextService.deleteNetworkContext(uuid, organizationId, tenantId, () => {
      toast.success('Network context deleted.');
      setDeleted(true);
    });
  }

  if (deleted) {
    return <Navigate to={ApiRoutes.CONTEXT.NETWORKS.INDEX} />
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
                <li className="breadcrumb-item"><a href={ApiRoutes.CONTEXT.NETWORKS.INDEX}>Networks</a></li>
                <li className="breadcrumb-item active">{context.cidr}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Context of Network <span className="machine-data">{context.cidr}</span>
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <WithPermission permission="network_context_manage">
                <button className="btn btn-danger" onClick={onDelete} disabled={deleting}>{deleting ? "Please wait ..." : "Delete"}</button>&nbsp;
                <a className="btn btn-secondary"
                   href={ApiRoutes.CONTEXT.NETWORKS.EDIT(context.uuid)}>
                  Edit
                </a>&nbsp;
              </WithPermission>
              <a className="btn btn-primary" href={ApiRoutes.CONTEXT.NETWORKS.INDEX}>Back</a>
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
                  <dd>{context.name ? <span className="context-name">{context.name}</span> :
                      <span className="text-muted">None</span>}</dd>
                  <dt>Description</dt>
                  <dd>{context.description ? context.description : <span className="text-muted">None</span>}</dd>
                  <dt>Matches</dt>
                  <dd>
                    <dl className="cidr-calculator mt-1 mb-0 ms-0">
                      <dt>From</dt>
                      <dd className="machine-data">{range.from}</dd>
                      <dt>To</dt>
                      <dd className="machine-data">{range.to}</dd>
                    </dl>
                  </dd>
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