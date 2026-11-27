import React, {useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import SectionMenuBar from "../../shared/SectionMenuBar";
import ApiRoutes from "../../../util/ApiRoutes";
import {BSSID_MENU_ITEMS} from "./BSSIDMenuItems";
import Dot11Service from "../../../services/Dot11Service";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import {Presets} from "../../shared/timerange/TimeRange";
import TimelinesService from "../../../services/TimelinesService";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";
import Timeline from "../../shared/timelines/Timeline";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import TimelineFilters from "../../shared/timelines/TimelineFilters";
import TimelineTapTable from "../../shared/timelines/TimelineTapTable";
import TruncatedList from "../../shared/TruncatedList";
import {toast} from "react-toastify";
import moment from "moment";
import {TimelineActivityHistogram} from "../../shared/timelines/TimelineActivityHistogram";

const dot11Service = new Dot11Service();
const timelinesService = new TimelinesService();

export default function BSSIDTimelinePage() {

  const {bssidParam} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [bssid, setBSSID] = useState(null);
  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_DAYS_7)

  const [events, setEvents] = useState(null);

  const [page, setPage] = useState(1);
  const perPage = 50;

  const [revision, setRevision] = useState(new Date());

  const [notFound, setNotFound] = useState(false);

  const [filters, setFilters] = useState([
    {name: "Gone/Disappeared", value: "GONE", include: true},
    {name: "Active Channel Change", value: "DOT11_BSSID_ACTIVE_CHANNEL", include: true},
    {name: "SSID Change", value: "DOT11_BSSID_SSID_DIFF", include: true},
    {name: "Fingerprints Change", value: "DOT11_BSSID_FINGERPRINT_DIFF", include: true},
    {name: "Strongest Tap", value: "DOT11_BSSID_STRONGEST_TAP", include: true}
  ]);

  const updateFilter = (filter, state) => {
    setFilters(prev =>
      prev.map(f =>
        f.value === filter ? { ...f, include: state } : f
      )
    );
  };

  usePageTitle(bssid ? `BSSID Timeline: ${bssid.summary.bssid.address}` : "BSSID Timeline");

  useEffect(() => {
    setBSSID(null);
    dot11Service.findBSSID(bssidParam, "*", (response) => {
      setNotFound(false);
      setBSSID(response.data)
    }, (error) => {
      if (error && (error.response.status === 404 || error.response.status === 400)) {
        setNotFound(true);
      } else {
        toast.error("Could not fetch BSSID.")
      }
    });
  }, [bssidParam]);

  useEffect(() => {
    if (bssid) {
      setEvents(null);

      const excludedEventTypes = filters.filter(f => !f.include).map(f => f.value);
      timelinesService.findAllOfAddress(
        "DOT11_BSSID",
        bssid.summary.bssid.address,
        organizationId,
        tenantId,
        timeRange,
        excludedEventTypes,
        perPage,
        (page-1)*perPage,
        setEvents
      );
    }
  }, [bssid, organizationId, tenantId, timeRange, filters, perPage, page, revision])

  if (notFound) {
    return (
      <div className="alert alert-warning">BSSID not found. It may have been retention-cleaned.</div>
    )
  }

  if (!bssid || !events) {
    return <LoadingSpinner />
  }

  const ssids = (events.has_hidden_ssid ? [<span key="hidden" className="text-muted">&lt;hidden&gt;</span>] : [])
      .concat(events.ssids);

  const limited = (list, noun) => {
    if (!events.limited_lists.includes(list)) {
      return null;
    }

    return (
        <p className="text-muted mt-2 mb-0">
          Timeline events hold more {noun} of this BSSID. Only {events.list_limit.toLocaleString("en-US")} of them
          are listed.
        </p>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX}>Access Points</Link></li>
              <li className="breadcrumb-item">{bssid.summary.bssid.address}</li>
              <li className="breadcrumb-item active" aria-current="page">Timeline</li>
            </ol>
          </nav>
        </div>
        <div className="col-md-2">
          <a href="https://go.nzyme.org/wifi-timelines" className="btn btn-outline-secondary float-end" target="_blank">
            Help
          </a>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={BSSID_MENU_ITEMS(bssid.summary.bssid.address)}
                          activeRoute={ApiRoutes.DOT11.NETWORKS.BSSIDS.TIMELIME(bssid.summary.bssid.address) }/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>
            BSSID &quot;{bssid.summary.bssid.address} ({bssid.summary.bssid.oui ? bssid.summary.bssid.oui : "Unknown Vendor"})&quot;
            Timeline
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters"
                                     slim={true}
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange} />

              <TimelineFilters onFilterChange={updateFilter} filters={filters} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Taps" slim={true} helpLink="https://go.nzyme.org/wifi-timelines-lists" />

              <p>The following taps observed the BSSID in the selected timeframe:</p>

              <TimelineTapTable taps={events.taps} />

              {events.taps_complete_since && events.taps_retention_days ?
                  <p className="text-muted mt-2 mb-0">
                    Taps that never had the strongest signal of the BSSID are only listed for the
                    last {events.taps_retention_days} days, which is the configured retention time of WiFi data.
                  </p> : null}

              {events.taps_complete_since && !events.taps_retention_days ?
                  <p className="text-muted mt-2 mb-0">
                    Taps that never had the strongest signal of the BSSID are only listed
                    since {moment(events.taps_complete_since).format("YYYY-MM-DD HH:mm")}, when timelines started to
                    record them.
                  </p> : null}

              {limited("taps", "taps")}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-5">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="SSIDs" slim={true} helpLink="https://go.nzyme.org/wifi-timelines-lists" />

              <p>SSIDs advertised by the BSSID in the selected timeframe:</p>

              {ssids.length === 0 ? <span className="text-muted">None</span>
                  : <TruncatedList items={ssids} limit={5} />}

              {limited("ssids", "SSIDs")}
            </div>
          </div>
        </div>

        <div className="col-7">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Fingerprints" slim={true} helpLink="https://go.nzyme.org/wifi-timelines-lists" />

              <p>Fingerprints of the BSSID in the selected timeframe:</p>

              {events.fingerprints.length === 0 ? <span className="text-muted">None</span>
                  : <TruncatedList items={events.fingerprints} limit={5} />}

              {limited("fingerprints", "fingerprints")}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Activity Histogram"
                                     slim={true}
                                     refreshAction={() => setRevision(new Date())} />

              <TimelineActivityHistogram data={events.activity_histogram} address={bssid} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Events"
                                     slim={true}
                                     refreshAction={() => setRevision(new Date())} />

              <Timeline events={events}
                        addressLastSeen={bssid.summary.last_seen}
                        page={page}
                        setPage={setPage}
                        perPage={perPage }/>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}