import React, {useEffect, useState} from "react";
import Dot11Service from "../../../../services/Dot11Service";
import TimelinesService from "../../../../services/TimelinesService";
import {Link, useParams} from "react-router-dom";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {useUrlTimeRange} from "../../../shared/timerange/TimeRangeSelector";
import {Presets} from "../../../shared/timerange/TimeRange";
import usePageTitle from "../../../../util/UsePageTitle";
import {toast} from "react-toastify";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import TimelineFilters from "../../../shared/timelines/TimelineFilters";
import {TimelineActivityHistogram} from "../../../shared/timelines/TimelineActivityHistogram";
import Timeline from "../../../shared/timelines/Timeline";
import {SSID_MENU_ITEMS} from "./SSIDMenuItems";

const dot11Service = new Dot11Service();
const timelinesService = new TimelinesService();

export default function SSIDTimelinePage() {

  const {bssidParam} = useParams();
  const {ssidParam} = useParams();
  const {frequencyParam} = useParams(); // needed to navigate back to SSID

  const [organizationId, tenantId] = useSelectedTenant();

  const [ssid, setSSID] = useState(null);
  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_DAYS_7)

  const [events, setEvents] = useState(null);

  const [page, setPage] = useState(1);
  const perPage = 50;

  const [revision, setRevision] = useState(new Date());

  const [notFound, setNotFound] = useState(false);

  const [filters, setFilters] = useState([
    {name: "Gone/Disappeared", value: "GONE", include: true},
    {name: "Active Channel Change", value: "DOT11_SSID_ACTIVE_CHANNEL", include: true},
    {name: "Rates Change", value: "DOT11_SSID_RATES_DIFF", include: true},
    {name: "Security Protocols Change", value: "DOT11_SSID_SECURITY_PROTOCOLS_DIFF", include: true},
    {name: "Security Suites Change", value: "DOT11_SSID_SECURITY_SUITES_DIFF", include: true},
    {name: "Fingerprints Change", value: "DOT11_SSID_FINGERPRINTS_DIFF", include: true}
  ]);

  const updateFilter = (filter, state) => {
    setFilters(prev =>
      prev.map(f =>
        f.value === filter ? { ...f, include: state } : f
      )
    );
  };

  usePageTitle(ssid ? `SSID Timeline: ${ssid.ssid}` : "SSID Timeline");

  useEffect(() => {
    setSSID(null);
    dot11Service.findSSIDOfBSSID(bssidParam, ssidParam, Presets.ALL_TIME, "*", (response) => {
      setNotFound(false);
      setSSID(response.data)
    }, (error) => {
      if (error && (error.response.status === 404 || error.response.status === 400)) {
        setNotFound(true);
      } else {
        toast.error("Could not fetch SSID.")
      }
    });
  }, [bssidParam, ssidParam]);

  useEffect(() => {
    if (ssid) {
      setEvents(null);

      const excludedEventTypes = filters.filter(f => !f.include).map(f => f.value);
      timelinesService.findAllOfAddress(
        "DOT11_SSID",
        encodeURIComponent(ssid.bssid.address + "/" + ssidParam),
        organizationId,
        tenantId,
        timeRange,
        excludedEventTypes,
        perPage,
        (page-1)*perPage,
        setEvents
      );
    }
  }, [ssid, organizationId, tenantId, timeRange, filters, perPage, page, revision])

  if (notFound) {
    return (
      <div className="alert alert-warning">SSID not found. It may have been retention-cleaned.</div>
    )
  }

  if (!ssid || !events) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.OVERVIEW}>WiFi</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX}>Access Points</Link></li>
              <li className="breadcrumb-item">{ssid.bssid.address}</li>
              <li className="breadcrumb-item">SSIDs</li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.DOT11.NETWORKS.SSIDS.DETAILS(ssid.bssid.address, ssid.ssid, frequencyParam)}>{ssid.ssid}</Link></li>
              <li className="breadcrumb-item active">Timeline</li>
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
          <SectionMenuBar items={SSID_MENU_ITEMS(ssid.bssid.address, ssid.ssid, frequencyParam)}
                          activeRoute={ApiRoutes.DOT11.NETWORKS.SSIDS.TIMELINE(ssid.bssid.address, ssid.ssid, frequencyParam)}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>
            SSID &quot;{ssid.ssid}&quot; on
            BSSID &quot;{ssid.bssid.address} ({ssid.bssid.oui ? ssid.bssid.oui : "Unknown Vendor"})&quot; Timeline
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
              <CardTitleWithControls title="Activity Histogram"
                                     slim={true}
                                     refreshAction={() => setRevision(new Date())} />

              <TimelineActivityHistogram data={events.activity_histogram} />
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
                        addressLastSeen={ssid.last_seen}
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