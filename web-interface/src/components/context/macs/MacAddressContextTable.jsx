import React, {useEffect, useState} from "react";
import Paginator from "../../misc/Paginator";
import ContextService from "../../../services/ContextService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ApiRoutes from "../../../util/ApiRoutes";
import FirstContextIpAddress from "../../shared/context/macs/details/FirstContextIpAddress";
import FirstContextHostname from "../../shared/context/macs/details/FirstContextHostname";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";

const contextService = new ContextService();

function MacAddressContextTable({addressFilter, addressFilterRevision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [context, setContext] = useState(null);

  const perPage = 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setContext(null);
    contextService.findAllMacAddressContext(organizationId, tenantId, addressFilter, setContext, perPage, (page - 1) * perPage);
  }, [page, organizationId, tenantId, addressFilterRevision]);

  if (!context) {
    return <LoadingSpinner />
  }

  if (context.total === 0) {
    return (
        <React.Fragment>
          <div className="alert alert-info mb-0">No context configurations found.</div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>MAC Address</th>
            <th>Name</th>
            <th>IP Addresses</th>
            <th>Hostnames</th>
            <th>Description</th>
          </tr>
          </thead>
          <tbody>
          {context.mac_addresses.map((m, i) => {
            return (
                <tr key={i}>
                  <td>
                    <a href={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(m.uuid)}>
                      {m.mac_address}
                    </a>{' '}

                    {m.mac_address_is_randomized ?
                        <i className="fa-solid fa-triangle-exclamation text-danger cursor-help"
                           title="This is a randomized MAC address."/>
                        : null}
                  </td>
                  <td>{m.name ? m.name : <span className="text-muted">n/a</span>}</td>
                  <td><FirstContextIpAddress addresses={m.transparent_ip_addresses} /></td>
                  <td><FirstContextHostname hostnames={m.transparent_hostnames} /></td>
                  <td>{m.description ? m.description : <span className="text-muted">n/a</span>}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={context.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default MacAddressContextTable;