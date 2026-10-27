import {Link} from "react-router-dom";
import React from "react";

import numeral from "numeral";
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
            <td className="text-muted" title="Data size not available for individual organizations and tenants.">
              n/a
            </td>
            <td>{numeral(org.total_sizes[category].rows).format("0,0")}</td>
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