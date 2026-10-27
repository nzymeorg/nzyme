import {Link} from "react-router-dom";
import React from "react";

import numeral from "numeral";
import ApiRoutes from "../../../util/ApiRoutes";
import SystemService from "../../../services/SystemService";

const systemService = new SystemService();

export default function TenantDatabaseUsageTableRows(props) {

  const tenants = props.tenants;
  const category = props.category;
  const onPurge = props.onPurge;
  const skipSizeColumns = props.skipSizeColumns;

  const purge = (e, category, organizationId, tenantId) => {
    e.preventDefault();

    if (!confirm("Really purge all data in the selected data category? This cannot be undone.")) {
      return;
    }

    systemService.purgeDatabaseTenantCategory(category, organizationId, tenantId, onPurge);
  }

  return tenants.map((tenant, i) => {
    if (!tenant.categories[category]) {
      return null;
    }

    return (
        <tr key={i}>
          <td>
            <span className="indent-left-2">
              <i className="fa-solid fa-arrow-turn-up fa-rotate-90" style={{marginRight: 8}}></i>
              Tenant: {' '}
              <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(tenant.organization_id, tenant.tenant_id)}>
                {tenant.tenant_name}
              </Link>
            </span>
          </td>
          {skipSizeColumns ? null : <td className="text-muted" title="Data size not available for individual organizations and tenants.">n/a</td> }
          <td>{numeral(tenant.categories[category].rows).format("0,0")}</td>
          <td>{numeral(tenant.categories[category].retention_days).format("0,0")} Days</td>
          <td>
            <a href="#" onClick={(e) => purge(e, category, tenant.organization_id, tenant.tenant_id)}>Purge</a>
          </td>
        </tr>
    )
  });

}