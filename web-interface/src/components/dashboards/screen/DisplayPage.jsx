import React, {useEffect, useMemo, useRef, useState} from "react";
import moment from "moment-timezone";
import Store from "../../../util/Store";
import RESTClient from "../../../util/RESTClient";
import usePageTitle from "../../../util/UsePageTitle";
import DarkMode from "../../layout/DarkMode";
import DashboardScreen from "./DashboardScreen";
import DashboardSources from "./DashboardSources";

const axios = require('axios')

const TOKEN_PREFIX = "nzd_";
const TOKEN_KEY = "display_token";
const RELOADED_AT_KEY = "display_reloaded_at";

const REQUEST_TIMEOUT_SECONDS = 15;
const UNAUTHORIZED_RETRY_SECONDS = 60;
const MAXIMUM_RETRY_SECONDS = 300;
const FAILED_RESTART_SECONDS = 60;
const MINIMUM_RELOAD_INTERVAL_MS = 10*60*1000;
const MAXIMUM_UPTIME_MS = 24*60*60*1000;
const MAXIMUM_CLOCK_DRIFT_MS = 10*1000;
const FIRST_REPORT_SECONDS = 2;
const STALE_AFTER_REFRESHES = 3;
const MINIMUM_STALE_SECONDS = 60;

const takeToken = () => {
  const fragment = window.location.hash.replace(/^#/, "");

  if (fragment.startsWith(TOKEN_PREFIX)) {
    Store.set(TOKEN_KEY, fragment);
    window.history.replaceState(null, "", window.location.pathname);
  }

  return Store.get(TOKEN_KEY);
}

// A reload into an error page would never recover, so the page is only replaced when the node can serve it.
const reloadWhenReachable = () => {
  const reloadedAt = Store.get(RELOADED_AT_KEY);
  if (reloadedAt && Date.now() - reloadedAt < MINIMUM_RELOAD_INTERVAL_MS) {
    return;
  }

  fetch(window.location.pathname, {cache: "no-store"}).then((response) => {
    if (response.ok) {
      Store.set(RELOADED_AT_KEY, Date.now());
      window.location.reload();
    }
  }).catch(() => {});
}

// A node without these endpoints or a proxy error page answers with HTML and status 200.
const isDisplayState = (state) => {
  return state !== null && typeof state === "object" && Array.isArray(state.dashboards)
    && Number.isFinite(state.refresh_seconds) && Number.isFinite(state.rotation_seconds);
}

const isDashboardContent = (content) => {
  return content !== null && typeof content === "object" && Array.isArray(content.widgets)
    && content.data !== null && typeof content.data === "object";
}

const isTopology = (topology) => {
  return topology !== null && typeof topology === "object" && (topology.feed === null
    || (typeof topology.feed === "object" && Array.isArray(topology.feed.nodes) && Array.isArray(topology.feed.sessions)));
}

const minuteOfDay = (timeOfDay) => {
  const [hours, minutes] = timeOfDay.split(":");
  return parseInt(hours, 10)*60 + parseInt(minutes, 10);
}

const isLight = (display, time) => {
  if (display.theme !== "SCHEDULED") {
    return display.theme === "LIGHT";
  }

  const zone = moment.tz.zone(display.time_zone) ? display.time_zone : moment.tz.guess();
  const local = moment.tz(time, zone);
  const minute = local.hours()*60 + local.minutes();
  const from = minuteOfDay(display.light_from);
  const until = minuteOfDay(display.light_until);

  // The light period can span midnight.
  return from < until ? minute >= from && minute < until : minute >= from || minute < until;
}

class DisplayErrorBoundary extends React.Component {

  constructor(props) {
    super(props);
    this.state = {failed: false};
  }

  static getDerivedStateFromError() {
    return {failed: true};
  }

  componentDidCatch() {
    this.timer = setTimeout(() => this.setState({failed: false}), FAILED_RESTART_SECONDS*1000);
  }

  componentWillUnmount() {
    clearTimeout(this.timer);
  }

  render() {
    if (this.state.failed) {
      return (
        <React.Fragment>
          <DarkMode enabled={true} />
          <DashboardScreen message="This display stopped working. It restarts itself." />
        </React.Fragment>
      )
    }

    return this.props.children;
  }

}

export default function DisplayPage() {

  return (
    <DisplayErrorBoundary>
      <Display />
    </DisplayErrorBoundary>
  )

}

function Display() {

  const [token, setToken] = useState(takeToken);

  const [display, setDisplay] = useState(null);
  const [unauthorized, setUnauthorized] = useState(false);
  const [connected, setConnected] = useState(true);
  const [lastContact, setLastContact] = useState(null);
  const [clockOffset, setClockOffset] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [contents, setContents] = useState({});

  const loadedAt = useRef(Date.now());
  const loadedVersion = useRef(null);

  usePageTitle(display ? display.name : "Display");

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);

    return () => clearInterval(timer);
  }, []);

  // Opening another display link on a screen that already shows a display only changes the fragment.
  useEffect(() => {
    const onHashChange = () => {
      if (window.location.hash.replace(/^#/, "").startsWith(TOKEN_PREFIX)) {
        setToken(takeToken());
        setDisplay(null);
        setContents({});
      }
    }

    window.addEventListener("hashchange", onHashChange);

    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    if (!token) {
      return;
    }

    let timer = null;
    let stopped = false;
    let failures = 0;

    const schedule = (seconds) => {
      if (!stopped) {
        timer = setTimeout(poll, seconds*1000);
      }
    }

    const poll = () => {
      axios.get(RESTClient.buildUri("/display"), {
        headers: {Authorization: "Bearer " + token},
        params: {version: loadedVersion.current},
        timeout: REQUEST_TIMEOUT_SECONDS*1000
      }).then((response) => {
        if (stopped) {
          return;
        }

        const state = response.data;

        if (!isDisplayState(state)) {
          onFailure();
          return;
        }

        failures = 0;

        // The first contact cannot say which version this page is: it learns that from the answer. The next one
        // follows right away so that the node knows.
        const first = loadedVersion.current === null;
        if (first) {
          loadedVersion.current = state.node_version;
        }

        // Screens with a wrong clock show the time of the node. Smaller differences are request latency.
        const drift = moment(state.server_time).valueOf() - Date.now();
        setClockOffset(Math.abs(drift) > MAXIMUM_CLOCK_DRIFT_MS ? drift : 0);

        setDisplay(state);
        setUnauthorized(false);
        setConnected(true);
        setLastContact(Date.now());

        if (state.node_version !== loadedVersion.current || Date.now() - loadedAt.current > MAXIMUM_UPTIME_MS) {
          reloadWhenReachable();
        }

        schedule(first ? FIRST_REPORT_SECONDS : state.refresh_seconds);
      }).catch((error) => {
        if (stopped) {
          return;
        }

        if (error.response && error.response.status === 401) {
          failures = 0;
          setUnauthorized(true);
          schedule(UNAUTHORIZED_RETRY_SECONDS);
          return;
        }

        onFailure();
      });
    }

    const onFailure = () => {
      failures += 1;
      setConnected(false);
      schedule(Math.min(5*Math.pow(2, failures - 1), MAXIMUM_RETRY_SECONDS));
    }

    poll();

    return () => {
      stopped = true;
      clearTimeout(timer);
    }
  }, [token]);

  const rotation = display && display.dashboards.length > 0
    ? Math.floor((now + clockOffset)/(display.rotation_seconds*1000)) % display.dashboards.length : 0;
  const shownUuid = display && display.dashboards.length > 0 ? display.dashboards[rotation].uuid : null;

  // The content of the dashboard on screen is loaded with every contact and when the rotation moves on.
  useEffect(() => {
    if (!token || !shownUuid) {
      return;
    }

    let stopped = false;

    axios.get(RESTClient.buildUri("/display/dashboards/" + shownUuid), {
      headers: {Authorization: "Bearer " + token},
      timeout: REQUEST_TIMEOUT_SECONDS*1000
    }).then((response) => {
      if (!stopped && isDashboardContent(response.data)) {
        setContents((previous) => {
          return {...previous, [shownUuid]: {content: response.data, loadedAt: Date.now(), failed: false}};
        });
      }
    }).catch(() => {
      if (!stopped) {
        setContents((previous) => {
          return previous[shownUuid] ? {...previous, [shownUuid]: {...previous[shownUuid], failed: true}} : previous;
        });
      }
    });

    return () => {
      stopped = true;
    }
  }, [token, shownUuid, lastContact]);

  const sources = useMemo(() => ({
    key: token + ":" + shownUuid,
    topology: (widgetId, onSuccess, onError) => {
      axios.get(RESTClient.buildUri("/display/dashboards/" + shownUuid + "/widgets/" + widgetId + "/topology"), {
        headers: {Authorization: "Bearer " + token},
        timeout: REQUEST_TIMEOUT_SECONDS*1000
      }).then((response) => {
        if (isTopology(response.data)) {
          onSuccess(response.data);
        } else {
          onError(null);
        }
      }).catch(onError);
    }
  }), [token, shownUuid]);

  if (!token) {
    return (
      <React.Fragment>
        <DarkMode enabled={true} />
        <DashboardScreen
          message="This screen has no display link. Open the link of a display from the Dashboards section of Nzyme." />
      </React.Fragment>
    )
  }

  if (unauthorized) {
    return (
      <React.Fragment>
        <DarkMode enabled={true} />
        <DashboardScreen
          message="This display is not authorized. It was deleted, its link was replaced, it has expired, this network is not allowed or dashboards are disabled." />
      </React.Fragment>
    )
  }

  if (!display) {
    return (
      <React.Fragment>
        <DarkMode enabled={true} />
        <DashboardScreen message={connected ? "Connecting to Nzyme." : "Nzyme cannot be reached. Retrying."} />
      </React.Fragment>
    )
  }

  const time = now + clockOffset;
  const dashboards = display.dashboards;
  const position = rotation;
  const shown = shownUuid ? contents[shownUuid] : null;
  const staleAfterMs = Math.max(display.refresh_seconds*STALE_AFTER_REFRESHES, MINIMUM_STALE_SECONDS)*1000;

  return (
    <DashboardSources.Provider value={sources}>
      <DarkMode enabled={!isLight(display, time)} />
      <DashboardScreen name={dashboards.length === 0 ? null : dashboards[position].name}
                       message={dashboards.length === 0 ? "No dashboard is assigned to this display."
                         : (shown ? null : "Loading.")}
                       content={shown ? shown.content : null}
                       stale={shown ? (shown.failed || !connected) && now - shown.loadedAt > staleAfterMs : false}
                       status={shown ? {
                         dataAgeMs: now - shown.loadedAt,
                         connected: connected,
                         rotation: dashboards.length < 2 ? null : {
                           position: position + 1,
                           count: dashboards.length,
                           nextMs: display.rotation_seconds*1000 - time % (display.rotation_seconds*1000)
                         }
                       } : null}
                       display={display.name + (display.location ? " / " + display.location : "")}
                       time={time}
                       timeZone={display.time_zone}
                       clockFormat={display.clock_format}
                       scalePercent={display.scale_percent}
                       hideCursor={display.hide_cursor === true} />
    </DashboardSources.Provider>
  )

}
