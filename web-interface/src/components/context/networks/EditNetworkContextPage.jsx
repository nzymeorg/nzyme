import {Navigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import NetworkContextForm from "./NetworkContextForm";

const contextService = new ContextService();

export default function EditNetworkContextPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);
  const [updated, setUpdated] = useState(false);

  usePageTitle(context ? `Edit Network Context: ${context.cidr}` : "Edit Network Context");

  useEffect(() => {
    contextService.findNetworkContextByUuid(uuid, organizationId, tenantId, setContext);
  }, [uuid, organizationId, tenantId]);

  const onSubmit = (cidr, name, description, notes, organizationId, tenantId, onComplete) => {
    contextService.editNetworkContext(uuid, name, description, notes, organizationId, tenantId, () => {
      toast.success('Context updated.');
      onComplete();
      setUpdated(true);
    }, () => {
      onComplete();
    });
  }

  if (updated) {
    return <Navigate to={ApiRoutes.CONTEXT.NETWORKS.SHOW(context.uuid)} />
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
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.CONTEXT.NETWORKS.SHOW(context.uuid)}>
                    {context.cidr}
                  </a>
                </li>
                <li className="breadcrumb-item active">Edit</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Edit Context of Network <span className="machine-data">{context.cidr}</span>
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <a className="btn btn-primary"
                 href={ApiRoutes.CONTEXT.NETWORKS.SHOW(context.uuid)}>
                Back
              </a>
            </span>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit Network Context</h3>

                <NetworkContextForm submitText={"Update Context"}
                                    organizationId={context.organization_id}
                                    tenantId={context.tenant_id}
                                    cidrDisabled={true}
                                    cidr={context.cidr}
                                    name={context.name}
                                    description={context.description}
                                    notes={context.notes}
                                    onSubmit={onSubmit} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}