import React, {useEffect, useState} from "react";
import Paginator from "../../misc/Paginator";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import ApiRoutes from "../../../util/ApiRoutes";

const contextService = new ContextService();

export default function NetworkContextTable() {

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    contextService.findAllNetworkContext(organizationId, tenantId, setContext, perPage, (page - 1) * perPage);
  }, [page, organizationId, tenantId]);

  if (!context) {
    return <LoadingSpinner />
  }

  if (context.total === 0) {
    return (
        <React.Fragment>
          <div className="alert alert-info mb-0">No context has been created yet.</div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>CIDR</th>
            <th>Name</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
          {context.networks.map((n, i) => {
            return (
                <tr key={i}>
                  <td><a href={ApiRoutes.CONTEXT.NETWORKS.SHOW(n.uuid)}>{n.cidr}</a></td>
                  <td>{n.name ? n.name : <span className="text-muted">n/a</span>}</td>
                  <td>{n.description ? n.description : <span className="text-muted">n/a</span>}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={context.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}