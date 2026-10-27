import React, {useState} from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import MacAddressContextForm from "./MacAddressContextForm";
import ContextService from "../../../services/ContextService";
import {toast} from "react-toastify";
import {Link, Navigate, useLocation} from "react-router-dom";
import usePageTitle from "../../../util/UsePageTitle";

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

const contextService = new ContextService();

function CreateMacAddressContextPage() {

  usePageTitle("Create MAC Address Context");

  let urlQuery = useQuery()
  const [complete, setComplete] = useState(false);

  const [errorMessage, setErrorMessage] = useState(null);

  const passedUrlMacAddress = urlQuery.get("address") ? urlQuery.get("address") : "";

  const onSubmit = (macAddress, name, description, notes, organizationId, tenantId, onComplete) => {
    contextService.createMacAddressContext(
        macAddress,
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
    return <Navigate to={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">Context</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}>MAC Addresses</Link>
                </li>
                <li className="breadcrumb-item active">Create</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <h1>
              Context: MAC Addresses
            </h1>
          </div>

          <div className="col-md-4">
            <span className="float-end">
              <Link className="btn btn-primary" to={ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX}>Back</Link>
            </span>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Create MAC Address Context</h3>

                <MacAddressContextForm submitText={"Add Context"}
                                       onSubmit={onSubmit}
                                       macAddress={passedUrlMacAddress}
                                       errorMessage={errorMessage} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateMacAddressContextPage;