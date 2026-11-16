import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import numeral from "numeral";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import TapsService from "../../../../services/TapsService";
import {UserContext} from "../../../../App";
import {apiErrorMessage, userHasPermission} from "../../../../util/Tools";
import {SEGMENTATION_MENU_ITEMS} from "../SegmentationMenuItems";
import {deleteSegmentQuestion, RESTAMP_NOTICE} from "../SegmentationFormat";
import {isBehindLastPage} from "../SegmentationBulkActions";
import {SEGMENTS_FILTER_FIELDS} from "./SegmentsFilterFields";
import SegmentsTable from "./SegmentsTable";
import SegmentSuggestionsTable from "./SegmentSuggestionsTable";

const segmentationService = new SegmentationService();
const tapsService = new TapsService();

const SHOWN_SKIPPED = 10;

export default function SegmentsPage() {

  usePageTitle("Ethernet Segments");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [filters, setFilters] = useUrlFilters(SEGMENTS_FILTER_FIELDS);
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");

  const [segments, setSegments] = useState(null);
  const [page, setPage] = useState(1);
  const [suggestions, setSuggestions] = useState(null);
  const [suggestionLimit, setSuggestionLimit] = useState(null);
  const [status, setStatus] = useState(null);
  const [taps, setTaps] = useState(null);
  const [deleteError, setDeleteError] = useState(null);
  const [skipped, setSkipped] = useState(null);
  const [importing, setImporting] = useState(false);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
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
    setSegments(null);
    segmentationService.findAllSegments(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, (response) => {
        if (isBehindLastPage(response.segments, response.total, page)) {
          setPage(Math.ceil(response.total / perPage));
        } else {
          setSegments(response);
        }
      }
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    setSuggestions(null);
    segmentationService.findSegmentSuggestions(organizationId, tenantId, (found, limitPerSource) => {
      setSuggestions(found);
      setSuggestionLimit(limitPerSource);
    });
    segmentationService.getStatus(organizationId, tenantId, setStatus);
  }, [organizationId, tenantId, revision]);

  useEffect(() => {
    setTaps(null);
    tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setTaps(r.data.taps));
  }, [organizationId, tenantId]);

  const onDelete = (e, segment) => {
    e.preventDefault();

    const what = segment.kind === "EXTERNAL" ? "external network" : "segment";

    if (!confirm(deleteSegmentQuestion(segment))) {
      return;
    }

    setDeleteError(null);
    setSkipped(null);
    segmentationService.deleteSegment(segment.uuid, organizationId, tenantId, () => {
      toast.success(segment.kind === "EXTERNAL" ? "External network deleted." : "Segment deleted.");

      if (segments && segments.segments.length === 1 && page > 1) {
        setPage(page - 1);
      }

      setRevision(new Date());
    }, (error) => {
      toast.error("Could not delete the " + what + ".");
      setDeleteError({name: segment.name, message: apiErrorMessage(error, "Could not delete the " + what + ".")});
    });
  }

  const onImport = (e) => {
    e.preventDefault();

    if (!confirm("Create a segment or external network for every network that has context and is none yet? Name, " +
        "description and notes are taken over.")) {
      return;
    }

    setImporting(true);
    segmentationService.importNetworkContext(organizationId, tenantId, (created) => {
      setImporting(false);

      if (!created || created.length === 0) {
        toast.info("Nothing to import.");
      } else {
        toast.success(created.length === 1 ? "Created 1 segment from network context: " + created[0] + "."
          : "Created " + created.length + " segments from network context.");
      }

      setRevision(new Date());
    }, (error) => {
      setImporting(false);
      toast.error(apiErrorMessage(error, "Could not import network context."));
    });
  }

  const bulk = canEdit ? {
    organizationId: organizationId,
    tenantId: tenantId,
    filters: filters,
    countAll: (onCount, onError) => {
      segmentationService.findAllSegments(organizationId, tenantId, null, orderColumn, orderDirection, 1, 0,
        (response) => onCount(response.total), onError);
    },
    onDone: (result) => {
      setDeleteError(null);
      setSkipped(result.skipped && result.skipped.length > 0 ? result.skipped : null);
      setRevision(new Date());
    }
  } : null;

  const contextSuggestions = suggestions ? suggestions.filter((s) => s.source === "NETWORK_CONTEXT").length : 0;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={SEGMENTATION_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX} />
        </div>
      </div>

      {status && status.restamp_pending ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-info mb-0">
              <i className="fa-solid fa-circle-notch fa-spin"></i> {RESTAMP_NOTICE}
            </div>
          </div>
        </div> : null}

      {deleteError ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-danger mb-0">
              <strong>&quot;{deleteError.name}&quot; was not deleted.</strong> {deleteError.message}
            </div>
          </div>
        </div> : null}

      {skipped ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning alert-dismissible mb-0">
              <strong>
                {skipped.length === 1 ? "1 segment or external network was"
                  : numeral(skipped.length).format("0,0") + " segments and external networks were"} skipped and not
                deleted.
              </strong>
              <ul className="mb-0">
                {skipped.slice(0, SHOWN_SKIPPED).map((s) => <li key={s.uuid}>{s.name}: {s.reason}</li>)}
                {skipped.length > SHOWN_SKIPPED ?
                  <li>and {numeral(skipped.length - SHOWN_SKIPPED).format("0,0")} more</li> : null}
              </ul>
              <button type="button" className="btn-close" title="Dismiss" onClick={() => setSkipped(null)}></button>
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={SEGMENTS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Segments and External Networks"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Segments are named parts of your network, external networks named groups of addresses outside of
                it. Addresses in neither show as <em>Unassigned</em> or <em>Internet</em>.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.CREATE} className="btn btn-sm btn-primary">
                    Create Segment
                  </Link>
                </p> : null}

              <SegmentsTable segments={segments}
                             taps={taps}
                             canEdit={canEdit}
                             onDelete={onDelete}
                             page={page}
                             setPage={setPage}
                             perPage={perPage}
                             orderColumn={orderColumn}
                             setOrderColumn={applyOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={applyOrderDirection}
                             filterFields={SEGMENTS_FILTER_FIELDS}
                             setFilters={applyFilters}
                             bulk={bulk} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Suggested Segments"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Networks that are no segment yet, from network context, trusted DHCP servers and observed traffic.
              </p>

              {canEdit && contextSuggestions > 0 ?
                <p>
                  <button className="btn btn-sm btn-primary" onClick={onImport} disabled={importing}
                          title="Creates a segment or external network for every network that has context and is none yet">
                    {importing ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Importing ...</span>
                      : "Import all from network context"}
                  </button>
                </p> : null}

              <SegmentSuggestionsTable suggestions={suggestions} limitPerSource={suggestionLimit} canEdit={canEdit} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
