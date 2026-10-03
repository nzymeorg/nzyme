import React, {useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import ContextService from "../../../services/ContextService";
import {toast} from "react-toastify";
import {Navigate, useLocation} from "react-router-dom";
import usePageTitle from "../../../util/UsePageTitle";
import NetworkContextForm from "./NetworkContextForm";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

const contextService = new ContextService();

export default function CreateNetworkContextPage() {

  usePageTitle("Create Network Context");

  let urlQuery = useQuery()
  const [complete, setComplete] = useState(false);

  const [errorMessage, setErrorMessage] = useState(null);

  const [passedCidr, ignored] = useState(
      urlQuery.get("cidr") ? urlQuery.get("cidr") : ""
  );

  const onSubmit = (cidr, name, description, notes, organizationId, tenantId, onComplete) => {
    contextService.createNetworkContext(
        cidr,
        name,
        description,
        notes,
        organizationId,
        tenantId,
        () => {
          toast.success('Context created.');
          onComplete();
          setComplete(true);
        },
        (error) => {
          toast.error('Could not create context.');
          setErrorMessage(error.response.data.message);
          onComplete();
        }
    )
  }

  if (complete) {
    return <Navigate to={ApiRoutes.CONTEXT.NETWORKS.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">Context</li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.CONTEXT.NETWORKS.INDEX}>Networks</a>
                </li>
                <li className="breadcrumb-item active">Create</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Context: Networks
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <a className="btn btn-primary" href={ApiRoutes.CONTEXT.NETWORKS.INDEX}>Back</a>
            </span>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Create Network Context</h3>

                <NetworkContextForm submitText={"Add Context"}
                                    onSubmit={onSubmit}
                                    cidr={passedCidr}
                                    errorMessage={errorMessage} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}