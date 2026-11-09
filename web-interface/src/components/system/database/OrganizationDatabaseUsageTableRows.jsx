import {Link} from "react-router-dom";
import React from "react";

import {formatBytes, formatRows} from "./DatabaseUsageStatus";
import TenantDatabaseUsageTableRows from "./TenantDatabaseUsageTableRows";
import ApiRoutes from "../../../util/ApiRoutes";
import SystemService from "../../../services/SystemService";

const systemService = new SystemService();

export default function OrganizationDatabaseUsageTableRows(props) {

  const organizations = props.organizations;
  const category = props.category;
  const onPurge = props.onPurge;

  const purge = (e, category, organizationId) => {
    e.preventDefault();

    if (!confirm("Really purge all data in the selected data category? This cannot be undone.")) {
      return;
    }

    systemService.purgeDatabaseOrganizationCategory(category, organizationId, onPurge);
  }

  return organizations.map((org, i) => {
    if (!org.total_sizes[category]) {
      return null;
    }

    return (
        <React.Fragment key={i}>
          <tr>
            <td>
              <span className="indent-left">
                <i className="fa-solid fa-arrow-turn-up fa-rotate-90" style={{marginRight: 8}}></i>
                Organization: {' '}
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(org.organization_id)}>
                  {org.organization_name}
                </Link>
              </span>
            </td>
            <td>{formatBytes(org.total_sizes[category])}</td>
            <td>{formatRows(org.total_sizes[category])}</td>
            <td className="text-muted" title="Retention time is configured per tenant only.">
              n/a
            </td>
            <td>
              <a href="#" onClick={(e) => purge(e, category, org.organization_id)}>Purge</a>
            </td>
          </tr>
          <TenantDatabaseUsageTableRows tenants={org.tenants} category={category} onPurge={onPurge} />
        </React.Fragment>
    )
  });

}