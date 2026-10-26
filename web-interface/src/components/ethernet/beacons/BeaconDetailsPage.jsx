import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useLocation, useParams} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {Presets} from "../../shared/timerange/TimeRange";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";
import BeaconsService from "../../../services/ethernet/BeaconsService";
import BeaconType from "./BeaconType";
import BeaconClassification from "./BeaconClassification";
import BeaconInterval from "./BeaconInterval";
import BeaconNameAndDescription from "./BeaconNameAndDescription";
import BeaconSourcesTable from "./BeaconSourcesTable";
import BeaconCheckinsTable from "./BeaconCheckinsTable";
import BeaconEnrichment from "./BeaconEnrichment";
import BeaconCheckinScatterCharts from "./BeaconCheckinScatterCharts";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {BEACON_CHECKINS_FILTER_FIELDS} from "./BeaconCheckinsFilterFields";
import BeaconNotes from "./BeaconNotes";
import BeaconDestinationConnectionsTable from "./BeaconDestinationConnectionsTable";
import L4Address from "../shared/L4Address";

const beaconsService = new BeaconsService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function BeaconDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [beacon, setBeacon] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  const [sources, setSources] = useState(null);
  const [sourcesOrderColumn, setSourcesOrderColumn] = useState("last_seen");
  const [sourcesOrderDirection, setSourcesOrderDirection] = useState("DESC");
  const [sourcesPage, setSourcesPage] = useState(1);
  const sourcesPerPage = 15;

  const urlQuery = useQuery();
  const [checkinsFilters, setCheckinsFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), BEACON_CHECKINS_FILTER_FIELDS)
  );

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [points, setPoints] = useState(null);
  const [checkins, setCheckins] = useState(null);
  const [connections, setConnections] = useState(null);
  const [checkinsPage, setCheckinsPage] = useState(1);
  const checkinsPerPage = 25;

  const [revision, setRevision] = useState(new Date());

  usePageTitle(beacon ? `Beacon: ${beacon.name}` : "Ethernet Beacon Details");

  useEffect(() => {
    setBeacon(null);
    beaconsService.findBeacon(uuid, organizationId, tenantId, setBeacon, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setSources(null);
    beaconsService.findBeaconSources(uuid, organizationId, tenantId, timeRange, sourcesOrderColumn,
      sourcesOrderDirection, sourcesPerPage, (sourcesPage-1)*sourcesPerPage, setSources);
  }, [uuid, organizationId, tenantId, timeRange, sourcesOrderColumn, sourcesOrderDirection, sourcesPage, revision]);

  useEffect(() => {
    setPoints(null);
    beaconsService.findBeaconCheckinPoints(uuid, organizationId, tenantId, timeRange, checkinsFilters,
      setPoints);
  }, [uuid, organizationId, tenantId, timeRange, checkinsFilters, revision]);

  useEffect(() => {
    setConnections(null);
    beaconsService.findBeaconDestinationConnections(uuid, organizationId, tenantId, timeRange, setConnections);
  }, [uuid, organizationId, tenantId, timeRange, revision]);

  useEffect(() => {
    setCheckins(null);
    beaconsService.findBeaconCheckins(uuid, organizationId, tenantId, timeRange, checkinsFilters,
      checkinsPerPage, (checkinsPage-1)*checkinsPerPage, setCheckins);
  }, [uuid, organizationId, tenantId, timeRange, checkinsFilters, checkinsPage, revision]);

  const applyCheckinsFilters = (newFilters) => {
    setCheckinsPage(1);
    setCheckinsFilters(newFilters);
  }

  const applyTimeRange = (newTimeRange) => {
    setSourcesPage(1);
    setCheckinsPage(1);
    setTimeRange(newTimeRange);
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this beacon? It will be detected again under a new name if the devices keep " +
        "calling out.")) {
      return;
    }

    beaconsService.deleteBeacon(uuid, organizationId, tenantId, () => {
      toast.success("Beacon deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.BEACONS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Beacon not found. <Link to={ApiRoutes.ETHERNET.BEACONS.INDEX}>Back to Beacons</Link>
      </div>
    )
  }

  if (!beacon) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.BEACONS.INDEX}>Beacons</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{beacon.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            <BeaconType type={beacon.type} />{' '}
            &quot;{beacon.name}&quot;{' '}
            <BeaconClassification classification={beacon.classification} />
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.CREATE + "?beacon=" + beacon.uuid}
                 className="btn btn-secondary me-1"
                 title="Create an exclusion so this destination never shows up as a beacon again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Beacon</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.BEACONS.INDEX} className="btn btn-primary">Back to Beacons</Link>
        </div>
      </div>

      {beacon.matching_exclusions && beacon.matching_exclusions.length > 0 ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning mb-0">
              <strong>Excluded.</strong> This beacon matches the{' '}
              {beacon.matching_exclusions.length === 1 ? "exclusion" : "exclusions"}{' '}
              {beacon.matching_exclusions.map((ex, i) => {
                return (
                  <React.Fragment key={ex.uuid}>
                    {i > 0 ? ", " : ""}
                    <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(ex.uuid)} className="alert-link">&quot;{ex.name}&quot;</Link>
                  </React.Fragment>
                )
              })}. It no longer receives check-ins and does not raise alerts. Delete it or keep it for reference.
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Name and Description" />

              <BeaconNameAndDescription beacon={beacon}
                                        organizationId={organizationId}
                                        tenantId={tenantId}
                                        canEdit={canEdit}
                                        onChange={() => setRevision(new Date())} />
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Classification" />

              <dl className="mb-0">
                <dt>Classification</dt>
                <dd>
                  <BeaconClassification classification={beacon.classification}
                                        beaconUuid={beacon.uuid}
                                        organizationId={organizationId}
                                        tenantId={tenantId}
                                        enableEditMode={canEdit}
                                        onChange={() => setRevision(new Date())} />
                </dd>
                <dt>Last Changed</dt>
                <dd className="mb-0">
                  {beacon.classification_changed_at ?
                    <span title={moment(beacon.classification_changed_at).format()}>
                      {moment(beacon.classification_changed_at).fromNow()}
                    </span>
                    : <span className="text-muted">Never classified</span>}
                </dd>
              </dl>

              <h6 className="mt-4">Notes</h6>
              <BeaconNotes beacon={beacon}
                           organizationId={organizationId}
                           tenantId={tenantId}
                           canEdit={canEdit} />
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Type</dt>
                <dd><BeaconType type={beacon.type} /></dd>
                <dt>Destination</dt>
                <dd>
                  {!beacon.destination_host && beacon.destination_address_data ?
                    <L4Address address={beacon.destination_address_data} />
                    : <span className="machine-data">{beacon.destination}</span>}
                </dd>
                {beacon.destination_host && beacon.destination_address ?
                  <React.Fragment>
                    <dt>Most Recent Destination Address</dt>
                    <dd>
                      {beacon.destination_address_data ?
                        <L4Address address={beacon.destination_address_data} hidePort={true} />
                        : <span className="machine-data">{beacon.destination_address}</span>}
                    </dd>
                  </React.Fragment> : null}
                <dt>Devices Calling Out</dt>
                <dd>{numeral(beacon.source_count).format("0,0")}</dd>
                <dt>Median Interval</dt>
                <dd><BeaconInterval seconds={beacon.interval_seconds} jitter={beacon.interval_jitter} /></dd>
                <dt>Total Check-Ins</dt>
                <dd>{numeral(beacon.checkin_count).format("0,0")}</dd>
                <dt>First Seen</dt>
                <dd title={moment(beacon.first_seen).format()}>{moment(beacon.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd title={moment(beacon.last_seen).format()} className="mb-0">{moment(beacon.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Protocol Details" />

              <BeaconEnrichment beacon={beacon} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Devices"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Devices that called out to this destination at any point in the selected time range. Each device
                has its own cadence, and its check-in count and interval cover its whole history. The time range
                applies to the devices, check-ins and connections on this page.
              </p>

              <BeaconSourcesTable sources={sources}
                                  page={sourcesPage}
                                  setPage={setSourcesPage}
                                  perPage={sourcesPerPage}
                                  orderColumn={sourcesOrderColumn}
                                  setOrderColumn={(c) => { setSourcesPage(1); setSourcesOrderColumn(c); }}
                                  orderDirection={sourcesOrderDirection}
                                  setOrderDirection={(d) => { setSourcesPage(1); setSourcesOrderDirection(d); }}
                                  filterFields={BEACON_CHECKINS_FILTER_FIELDS}
                                  setFilters={applyCheckinsFilters} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Check-Ins"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <div className="mb-4">
                <Filters filters={checkinsFilters}
                         setFilters={applyCheckinsFilters}
                         fields={BEACON_CHECKINS_FILTER_FIELDS}
                         onMonitorsReady={() => {}} />
              </div>

              <BeaconCheckinScatterCharts points={points}
                                          timeRange={timeRange}
                                          setTimeRange={applyTimeRange} />

              <div className="mt-3">
                <BeaconCheckinsTable checkins={checkins}
                                     page={checkinsPage}
                                     setPage={setCheckinsPage}
                                     perPage={checkinsPerPage}
                                     filterFields={BEACON_CHECKINS_FILTER_FIELDS}
                                     setFilters={applyCheckinsFilters} />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Connections to Destination"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                All TCP and UDP connections to the destination address of this beacon. This
                includes the beacon connections themselves and shows what else devices in your network talk to the
                destination about.
              </p>

              <BeaconDestinationConnectionsTable connections={connections} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
