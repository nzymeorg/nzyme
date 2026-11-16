import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import {Presets} from "../../../shared/timerange/TimeRange";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {UserContext} from "../../../../App";
import {filtersApplied, userHasPermission} from "../../../../util/Tools";
import {SEGMENTATION_MENU_ITEMS} from "../SegmentationMenuItems";
import {isBehindLastPage} from "../SegmentationBulkActions";
import {SEGMENTATION_POLICIES_FILTER_FIELDS} from "./SegmentationPoliciesFilterFields";
import SegmentationPoliciesTable from "./SegmentationPoliciesTable";

const segmentationService = new SegmentationService();

export default function SegmentationPoliciesPage() {

  usePageTitle("Ethernet Segmentation Policies");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(SEGMENTATION_POLICIES_FILTER_FIELDS);

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const [policies, setPolicies] = useState(null);
  const [options, setOptions] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  const filtered = filtersApplied(filters);

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  const applyTimeRange = (newTimeRange) => {
    setPage(1);
    setTimeRange(newTimeRange);
  }

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  useEffect(() => {
    setPolicies(null);
    segmentationService.findAllPolicies(
      organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, perPage, (page-1)*perPage, (response) => {
        if (isBehindLastPage(response.policies, response.total, page)) {
          setPage(Math.ceil(response.total / perPage));
        } else {
          setPolicies(response);
        }
      }
    );
  }, [organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setOptions(null);
    segmentationService.findSelectorOptions(organizationId, tenantId, setOptions);
  }, [organizationId, tenantId, revision]);

  const bulk = canEdit ? {
    organizationId: organizationId,
    tenantId: tenantId,
    filters: filters,
    timeRange: timeRange,
    countAll: (onCount, onError) => {
      segmentationService.findAllPolicies(organizationId, tenantId, null, timeRange, orderColumn, orderDirection, 1, 0,
        (response) => onCount(response.total), onError);
    },
    onDone: () => setRevision(new Date())
  } : null;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={SEGMENTATION_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true} />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={SEGMENTATION_POLICIES_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Policies"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                A policy has a scope, like <em>all connections from the office network to the servers</em>, and
                lists all allowed crossings in that scope, like SSH and HTTPS. <strong>Every other attempted or
                successful connection in scope is reported as a finding.</strong> The state and the number of
                findings of each policy are judged since the start of the selected time range.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.CREATE} className="btn btn-sm btn-primary">
                    Create Policy
                  </Link>
                </p> : null}

              <SegmentationPoliciesTable policies={policies}
                                         options={options}
                                         filtered={filtered}
                                         page={page}
                                         setPage={setPage}
                                         perPage={perPage}
                                         orderColumn={orderColumn}
                                         setOrderColumn={applyOrderColumn}
                                         orderDirection={orderDirection}
                                         setOrderDirection={applyOrderDirection}
                                         filterFields={SEGMENTATION_POLICIES_FILTER_FIELDS}
                                         setFilters={applyFilters}
                                         bulk={bulk} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
