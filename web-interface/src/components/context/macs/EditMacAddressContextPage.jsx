import {Link, Navigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import MacAddressContextForm from "./MacAddressContextForm";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const contextService = new ContextService();

function MacAddressContextDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);
  const [updated, setUpdated] = useState(false);

  usePageTitle(context ? `Edit MAC Address Context: ${context.mac_address}` : "Edit MAC Address Context");

  useEffect(() => {
    contextService.findMacAddressContextByUuid(uuid, organizationId, tenantId, setContext);
  }, [uuid, organizationId, tenantId]);

  const onSubmit = (macAddress, name, description, notes, organizationId, tenantId, onComplete) => {
    contextService.editMacAddressContext(uuid, name, description, notes, organizationId, tenantId, () => {
      toast.success('Context updated.');
      onComplete();
      setUpdated(true);
    }, () => {
      onComplete();
    });
  }

  if (updated) {
    return <Navigate to={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(context.uuid)} />
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
                <li className="breadcrumb-item"><Link to={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}>MAC Addresses</Link></li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(context.uuid)}>
                    {context.mac_address}
                  </Link>
                </li>
                <li className="breadcrumb-item active">Edit</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Edit Context of MAC Address &quot;{context.mac_address}&quot;
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <Link className="btn btn-primary"
                 to={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(context.uuid)}>
                Back
              </Link>
            </span>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit MAC Address Context</h3>

                <MacAddressContextForm submitText={"Update Context"}
                                       organizationId={context.organization_id}
                                       tenantId={context.tenant_id}
                                       macAddressDisabled={true}
                                       macAddress={context.mac_address}
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

export default MacAddressContextDetailsPage;