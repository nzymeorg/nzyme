import React, {useEffect, useMemo, useState} from "react";
import {Link, useNavigate, useParams} from "react-router-dom";
import ApiRoutes from "../../util/ApiRoutes";
import usePageTitle from "../../util/UsePageTitle";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import DashboardsService from "../../services/DashboardsService";
import DashboardScreen from "./screen/DashboardScreen";
import DashboardSources from "./screen/DashboardSources";

const dashboardsService = new DashboardsService();

const REFRESH_SECONDS = 30;
const STALE_AFTER_REFRESHES = 3;

export default function DashboardViewPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const navigate = useNavigate();

  const [dashboard, setDashboard] = useState(null);
  const [content, setContent] = useState(null);
  const [contentAt, setContentAt] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [now, setNow] = useState(Date.now());

  usePageTitle(dashboard ? dashboard.name : "Dashboard");

  useEffect(() => {
    setDashboard(null);
    setNotFound(false);
    dashboardsService.findDashboard(uuid, organizationId, tenantId, setDashboard, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);

    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    setContent(null);

    let stopped = false;
    const load = () => {
      dashboardsService.findDashboardContent(uuid, organizationId, tenantId, (loaded) => {
        if (!stopped) {
          setContent(loaded);
          setContentAt(Date.now());
        }
      }, () => {});
    }

    load();
    const timer = setInterval(load, REFRESH_SECONDS*1000);

    return () => {
      stopped = true;
      clearInterval(timer);
    }
  }, [uuid, organizationId, tenantId]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === "Escape") {
        navigate(ApiRoutes.DASHBOARDS.DETAILS(uuid));
      }
    }

    window.addEventListener("keydown", onKeyDown);

    return () => window.removeEventListener("keydown", onKeyDown);
  }, [uuid, navigate]);

  const sources = useMemo(() => ({
    key: uuid,
    topology: (widgetId, onSuccess, onError) => {
      dashboardsService.findDashboardTopology(uuid, widgetId, organizationId, tenantId, onSuccess, onError);
    }
  }), [uuid, organizationId, tenantId]);

  const close = <Link to={notFound ? ApiRoutes.DASHBOARDS.INDEX : ApiRoutes.DASHBOARDS.DETAILS(uuid)}>Close</Link>;

  if (notFound) {
    return <DashboardScreen message="Dashboard not found." footerRight={close} />
  }

  if (!dashboard || !content) {
    return <DashboardScreen message="Loading." footerRight={close} />
  }

  return (
    <DashboardSources.Provider value={sources}>
      <DashboardScreen name={dashboard.name}
                       content={content}
                       stale={now - contentAt > STALE_AFTER_REFRESHES*REFRESH_SECONDS*1000}
                       status={{dataAgeMs: now - contentAt, connected: true, rotation: null}}
                       time={now}
                       footerRight={close} />
    </DashboardSources.Provider>
  )

}
