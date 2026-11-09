import React from "react";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import SystemService from "../../../../../services/SystemService";
import {humanReadableDatabaseCategoryName} from "../../../../../util/Tools";
import {toast} from "react-toastify";
import TenantDatabaseUsageTableRows from "../../../database/TenantDatabaseUsageTableRows";
import DatabaseUsageStatus, {formatBytes, formatRows} from "../../../database/DatabaseUsageStatus";

const systemService = new SystemService();

export default function OrganizationDatabaseTable(props) {

  const organization = props.organization;
  const sizes = props.sizes;
  const onRefresh = props.onRefresh;

  const onPurge = () => {
    onRefresh();
    toast.success('Data purge request submitted. It can take a while to complete.');
  }

  const purge = (e, category) => {
    e.preventDefault();

    if (!confirm("Really purge all data of this organization in the selected data category? This cannot be undone.")) {
      return;
    }

    systemService.purgeDatabaseOrganizationCategory(category, organization.id, onPurge);
  }

  if (!organization || !sizes) {
    return <LoadingSpinner />;
  }

  return (
    <React.Fragment>
    <DatabaseUsageStatus sizes={sizes} onRefresh={onRefresh}>
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Data Category</th>
          <th>Data Size</th>
          <th>Rows</th>
          <th>Retention Time</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {Object.keys(sizes.total_sizes).sort().map((key, i) => {
          return (
            <React.Fragment key={i}>
              <tr>
                <td><strong>{humanReadableDatabaseCategoryName(key)}</strong></td>
                <td>{formatBytes(sizes.total_sizes[key])}</td>
                <td>{formatRows(sizes.total_sizes[key])}</td>
                <td className="text-muted" title="Retention time is configured per tenant only.">n/a</td>
                <td>
                  <a href="#" onClick={(e) => purge(e, key)}>Purge</a>
                </td>
              </tr>
              <TenantDatabaseUsageTableRows tenants={sizes.tenants}
                                            category={key}
                                            onPurge={onPurge} />
            </React.Fragment>
        )
        })}
        </tbody>
      </table>
    </DatabaseUsageStatus>
    </React.Fragment>
  )

}