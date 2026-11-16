import React, {useContext, useEffect, useState} from "react";
import numeral from "numeral";
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
import {userHasPermission} from "../../../../util/Tools";
import {SEGMENTATION_MENU_ITEMS} from "../SegmentationMenuItems";
import {isBehindLastPage} from "../SegmentationBulkActions";
import {SEGMENTATION_FINDINGS_FILTER_FIELDS} from "./SegmentationFindingsFilterFields";
import SegmentationFindingsTable from "./SegmentationFindingsTable";

const segmentationService = new SegmentationService();

export default function SegmentationFindingsPage() {

  usePageTitle("Ethernet Segmentation Findings");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(SEGMENTATION_FINDINGS_FILTER_FIELDS);

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [orderColumn, setOrderColumn] = useState("last_seen");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [findings, setFindings] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

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
    setFindings(null);
    segmentationService.findAllFindings(
      organizationId, tenantId, filters, timeRange, null, orderColumn, orderDirection, perPage, (page-1)*perPage,
      (response) => {
        if (isBehindLastPage(response.findings, response.total, page)) {
          setPage(Math.ceil(response.total / perPage));
        } else {
          setFindings(response);
        }
      }
    );
  }, [organizationId, tenantId, filters, timeRange, orderColumn, orderDirection, page, revision]);

  const bulk = canEdit ? {
    organizationId: organizationId,
    tenantId: tenantId,
    filters: filters,
    timeRange: timeRange,
    policy: null,
    countAll: (onCount, onError) => {
      segmentationService.findAllFindings(organizationId, tenantId, null, timeRange, null, orderColumn, orderDirection,
        1, 0, (response) => onCount(response.total), onError);
    },
    onDone: () => setRevision(new Date())
  } : null;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={SEGMENTATION_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX} />
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
                       fields={SEGMENTATION_FINDINGS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Findings"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Attempted or successful connections that a policy does not allow, one finding per source and
                policy. Lists findings that were active in the selected time range.
              </p>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <SegmentationFindingsTable findings={findings}
                                         page={page}
                                         setPage={setPage}
                                         perPage={perPage}
                                         orderColumn={orderColumn}
                                         setOrderColumn={applyOrderColumn}
                                         orderDirection={orderDirection}
                                         setOrderDirection={applyOrderDirection}
                                         filterFields={SEGMENTATION_FINDINGS_FILTER_FIELDS}
                                         setFilters={applyFilters}
                                         bulk={bulk} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
