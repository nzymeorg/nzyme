import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import ClusterService from "../../../../services/ClusterService";
import NodesTable from "./NodesTable";
import EphemeralNodesConfiguration from "./EphemeralNodesConfiguration";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";

const clusterService = new ClusterService()

function fetchData(setNodes) {
  clusterService.findAllNodes(setNodes)
}

function NodesPage() {

  usePageTitle("Nodes");

  const [nodes, setNodes] = useState(null)

  useEffect(() => {
    fetchData(setNodes)
    const id = setInterval(() => fetchData(setNodes), 5000)
    return () => clearInterval(id)
  }, [setNodes])

  return (
      <div>
        <div className="row">
          <div className="col-12">
            <h1>Cluster &amp; Nodes</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <EphemeralNodesConfiguration setNodes={setNodes} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <NodesTable nodes={nodes} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Cluster Messaging</h3>

                <p>
                  An nzyme cluster is communicating between nodes to ensure operations. For debugging information,
                  you can access details of the inter-node messaging bus and tasks queue.
                </p>

                <Link to={ApiRoutes.SYSTEM.CLUSTER.MESSAGING.INDEX} className="btn btn-sm btn-secondary">
                  Open Cluster Messaging Page
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default NodesPage
