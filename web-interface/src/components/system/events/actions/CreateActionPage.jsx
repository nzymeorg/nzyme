import React, {useState} from "react";
import {Link, Navigate, Routes} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import CreateActionSelect from "../shared/forms/CreateActionSelect";
import CreateActionProxy from "../shared/forms/CreateActionProxy";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";

function CreateActionPage() {

  usePageTitle("Create Action");

  const [complete, setComplete] = useState(false);

  const [type, setType] = useState("");

  if (complete) {
    return <Navigate to={ApiRoutes.SYSTEM.EVENTS.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.SYSTEM.EVENTS.INDEX}>Events &amp; Actions</Link></li>
                <li className="breadcrumb-item">Actions</li>
                <li className="breadcrumb-item active" aria-current="page">Create</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-primary float-end" to={ApiRoutes.SYSTEM.EVENTS.INDEX}>Back</Link>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Create Action" helpLink="https://go.nzyme.org/alerting-action-types"/>

                <CreateActionSelect type={type} setType={setType} />
                <CreateActionProxy type={type} setComplete={setComplete} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateActionPage;