import React from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import WithPermission from "../../misc/WithPermission";
import usePageTitle from "../../../util/UsePageTitle";
import NetworkContextTable from "./NetworkContextTable";

export default function NetworksContextPage() {

  usePageTitle("Context: Networks");

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">Context</li>
                <li className="breadcrumb-item active">Networks</li>
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

          <div className="col-md-4 text-end">
            <a href="https://go.nzyme.org/context" className="btn btn-secondary">Help</a>{' '}
            <WithPermission permission="network_context_manage">
              <a className="btn btn-primary" href={ApiRoutes.CONTEXT.NETWORKS.CREATE}>Create Context</a>
            </WithPermission>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>All Networks with Context</h3>

                <NetworkContextTable />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}