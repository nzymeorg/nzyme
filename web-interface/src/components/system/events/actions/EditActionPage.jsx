import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import EventActionsService from "../../../../services/EventActionsService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import EditActionProxy from "../shared/forms/EditActionProxy";
import usePageTitle from "../../../../util/UsePageTitle";

const eventActionsService = new EventActionsService();

function EditActionPage() {

  usePageTitle("Edit Action");

  const { actionId } = useParams();

  const [action, setAction] = useState(null);

  const [complete, setComplete] = useState(false);

  useEffect(() => {
    eventActionsService.findAction(actionId, setAction)
  }, [actionId])


  if (complete) {
    return <Navigate to={ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(action.id)} />
  }

  if (!action) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.SYSTEM.EVENTS.INDEX}>Events &amp; Actions</Link></li>
                <li className="breadcrumb-item">Actions</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(action.id)}>{action.name}</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Edit</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-2">
            <Link className="btn btn-primary float-end"
               to={ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(action.id)}>Back</Link>
          </div>

          <div className="col-md-12">
            <h1>Event Action &quot;{action.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Edit Action</h3>

                    <div className="mb-3">
                      <label htmlFor="actionType" className="form-label">Action Type</label>
                      <input type="text"
                             className="form-control"
                             id="actionType"
                             value={action.action_type_human_readable}
                             disabled={true} />
                    </div>

                    <EditActionProxy action={action} type={action.action_type} setComplete={setComplete} />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )
}

export default EditActionPage;