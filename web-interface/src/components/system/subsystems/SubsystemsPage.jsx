import React from "react";
import SubsystemsConfiguration from "../../shared/SubsystemsConfiguration";
import SystemService from "../../../services/SystemService";
import usePageTitle from "../../../util/UsePageTitle";

const systemService = new SystemService();

export default function SubsystemsPage() {

  usePageTitle("Subsystems");

  return (
    <div>
      <div className="row">
        <div className="col-md-10">
          <h1>Subsystems</h1>
        </div>

        <div className="col-md-2">
          <a href="https://go.nzyme.org/subsystems" className="float-end btn btn-secondary">Help</a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <h3>Subsystem Configuration</h3>

              <p>
                This setting governs subsystem availability across the entire Nzyme cluster. <strong>You can also
                configure subsystems at the organization and tenant levels</strong>, with the higher-level configuration
                taking precedence.
              </p>

              <SubsystemsConfiguration dbUpdateCallback={systemService.updateSubsystemsConfiguration} />
            </div>
          </div>
        </div>
      </div>

    </div>
  )

}