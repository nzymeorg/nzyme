import React, {useEffect, useRef, useState} from 'react';
import {useLocation} from "react-router-dom";
import numeral from "numeral";
import {toast} from "react-toastify";
import Dot11Service from "../../../../services/Dot11Service";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {isBehindLastPage} from "../../../ethernet/segmentation/SegmentationBulkActions";
import HiddenAccessPointsTable from "./HiddenAccessPointsTable";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";

const dot11Service = new Dot11Service();

const ANCHOR = "hidden-access-points";
const NAVIGATION_BAR_HEIGHT = "70px";

export default function HiddenAccessPoints({configuration, cardsAboveLoaded}) {

  const [organizationId, tenantId] = useSelectedTenant();
  const {hash, key} = useLocation();

  const card = useRef(null);
  const scrolledFor = useRef(null);

  const [accessPoints, setAccessPoints] = useState(null);

  const [revision, setRevision] = useState(new Date());

  const [perPage, setPerPage] = useState(25);
  const [page, setPage] = useState(1);

  const [orderColumn, setOrderColumn] = useState("status");
  const [orderDirection, setOrderDirection] = useState("ASC");

  useEffect(() => {
    setAccessPoints(null);
  }, [organizationId, tenantId]);

  // Not reset while loading again: a spinner in place of the table changes the page height and moves the scroll position.
  useEffect(() => {
    const controller = new AbortController();

    dot11Service.findAllHiddenAccessPoints(
        organizationId, tenantId, orderColumn, orderDirection, perPage, (page-1)*perPage, (response) => {
          if (isBehindLastPage(response.access_points, response.total, page)) {
            setPage(Math.ceil(response.total / perPage));
          } else {
            setAccessPoints(response);
          }
        }, controller.signal
    );

    return () => controller.abort();
  }, [page, perPage, orderColumn, orderDirection, organizationId, tenantId, revision])

  // Links from alerts, the briefing and search point at this card. Once per navigation, and only after the cards above
  // have their height.
  useEffect(() => {
    if (accessPoints && configuration && cardsAboveLoaded && scrolledFor.current !== key
        && hash === "#" + ANCHOR && card.current) {
      scrolledFor.current = key;
      card.current.scrollIntoView();
    }
  }, [accessPoints, configuration, cardsAboveLoaded, hash, key]);

  const onChange = () => {
    setRevision(new Date());
  }

  const onApproveAll = (e) => {
    e.preventDefault();

    if (!confirm("Really approve all hidden access points? Ignored access points are skipped.")) {
      return;
    }

    dot11Service.approveAllHiddenAccessPoints(organizationId, tenantId, () => {
      toast.success("All hidden access points approved.");
      onChange();
    });
  }

  const onDeleteAll = (e) => {
    e.preventDefault();

    if (!confirm("Really delete all hidden access points? Each will reappear as unapproved access point " +
        "next time Nzyme records it.")) {
      return;
    }

    dot11Service.deleteAllHiddenAccessPoints(organizationId, tenantId, () => {
      toast.success("All hidden access points deleted.");
      onChange();
    });
  }

  const notRecording = () => {
    if (!configuration) {
      return null;
    }

    if (!configuration.is_enabled.value) {
      return <div className="alert alert-warning">Hidden access points are not recorded because monitoring is disabled.</div>
    }

    if (!configuration.hidden_access_points_enabled.value) {
      return (
          <div className="alert alert-warning">
            Hidden access points are not recorded. Enable <em>Record hidden access points</em> in the monitor
            configuration to record them.
          </div>
      )
    }

    return null;
  }

  const limitReached = () => {
    if (!accessPoints || accessPoints.total < accessPoints.maximum) {
      return null;
    }

    return (
        <div className="alert alert-warning">
          The list holds {numeral(accessPoints.maximum).format("0,0")} hidden access points, which is the limit.
          New hidden access points are not recorded until others are deleted.
        </div>
    )
  }

  const notice = notRecording();
  const hasEntries = accessPoints && accessPoints.total > 0;

  return (
      <div className="card" id={ANCHOR} ref={card} style={{scrollMarginTop: NAVIGATION_BAR_HEIGHT}}>
        <div className="card-body">
          <CardTitleWithControls title="Hidden Access Points"
                                 helpLink="https://go.nzyme.org/wifi-ssid-monitoring-hidden-access-points" />

          <p className="text-muted">
            Access points that advertise without a network name are recorded by their BSSID. Those not seen for 30
            days are removed.
          </p>

          {notice}
          {limitReached()}

          {hasEntries ?
              <div className="row align-items-center">
                <div className="col-auto">
                  <label className="col-form-label">Per Page:</label>
                </div>
                <div className="col-auto">
                  <select className="form-select"
                          value={perPage}
                          onChange={(e) => {e.preventDefault(); setPage(1); setPerPage(Number(e.target.value))}}>
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                    <option value={250}>250</option>
                  </select>
                </div>
                <div className="col text-end">
                  <button className="btn btn-secondary btn-sm me-1" onClick={onApproveAll}>Approve All</button>
                  <button className="btn btn-danger btn-sm" onClick={onDeleteAll}>Delete All</button>
                </div>
              </div> : null}

          {notice && accessPoints && !hasEntries ? null :
          <HiddenAccessPointsTable accessPoints={accessPoints}
                                   onChange={onChange}
                                   page={page}
                                   setPage={setPage}
                                   perPage={perPage}
                                   orderColumn={orderColumn}
                                   setOrderColumn={setOrderColumn}
                                   orderDirection={orderDirection}
                                   setOrderDirection={setOrderDirection} />}
        </div>
      </div>
  )

}
