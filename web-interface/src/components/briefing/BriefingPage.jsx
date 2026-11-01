import React, {useCallback, useContext, useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {UserContext} from "../../App";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../util/UsePageTitle";
import BriefingService from "../../services/BriefingService";
import Store from "../../util/Store";
import {Presets} from "../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../shared/timerange/TimeRangeUrl";
import BriefingHeader from "./BriefingHeader";
import BriefingBody from "./BriefingBody";

const briefingService = new BriefingService();

const TITLES = {
  MAIN: "Briefing",
  DOT11: "WiFi Briefing",
  ETHERNET: "Ethernet Briefing",
  BLUETOOTH: "Bluetooth Briefing"
}

const SINCE_MODES = ["LAST_VISIT", "LAST_SESSION", "TIME_RANGE"];

export default function BriefingPage({type}) {

  const title = TITLES[type];
  usePageTitle(title);

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);

  const location = useLocation();
  const navigate = useNavigate();

  const sinceFromUrl = new URLSearchParams(location.search).get("since");
  const storeKey = "briefing_since_" + type;
  const [since, setSinceState] = useState(() => {
    if (SINCE_MODES.includes(sinceFromUrl)) {
      return sinceFromUrl;
    }
    const stored = Store.get(storeKey);
    return SINCE_MODES.includes(stored) ? stored : "LAST_VISIT";
  });

  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24, "briefing");

  const [briefing, setBriefing] = useState(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);

  const setSince = (mode) => {
    setSinceState(mode);
    Store.set(storeKey, mode);

    const params = new URLSearchParams(location.search);
    params.set("since", mode);
    navigate({search: params.toString()}, {replace: true});
  }

  useEffect(() => {
    setBriefing(null);
    setLoading(true);
  }, [type]);

  const effectiveTimeRange = since === "TIME_RANGE" ? timeRange : null;
  const timeRangeKey = effectiveTimeRange ? JSON.stringify(effectiveTimeRange) : "";

  useEffect(() => {
    if (!organizationId || !tenantId) {
      return;
    }

    setLoading(true);
    setFailed(false);

    // Always every tap of the tenant, the tap selector stays off here.
    briefingService.getBriefing(type, organizationId, tenantId, "*", since, effectiveTimeRange, (data) => {
      setBriefing(data);
      setLoading(false);
    }, () => {
      setFailed(true);
      setLoading(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, organizationId, tenantId, since, timeRangeKey, revision]);

  const refresh = useCallback(() => setRevision((r) => r + 1), []);

  const markSeen = () => {
    briefingService.markSeen(type, () => {
      if (since !== "LAST_VISIT") {
        setSince("LAST_VISIT");
      }
      refresh();
    });
  }

  const noSubsystems = () => {
    if (!user.subsystems || user.subsystems.length > 0) {
      return null;
    }

    return (
      <div className="alert alert-info mt-3">
        Enable at least one Nzyme subsystem to see anything in this briefing.
      </div>
    )
  }

  return (
    <div className={"briefing briefing-" + type.toLowerCase() + (loading ? " briefing-loading" : "")}>
      <BriefingHeader since={since}
                      setSince={setSince}
                      timeRange={timeRange}
                      setTimeRange={setTimeRange}
                      loading={loading}
                      onMarkSeen={markSeen} />

      {noSubsystems()}

      {failed ? (
        <div className="alert alert-danger mt-3">
          The briefing could not be built. The request failed. Refresh to try again.
        </div>
      ) : null}

      {briefing ? (
        <BriefingBody key={briefing.generated_at} briefing={briefing} type={type} onRefresh={refresh} loading={loading} />
      ) : (
        !failed ? <BriefingSkeleton /> : null
      )}
    </div>
  )

}

function BriefingSkeleton() {
  return (
    <div className="briefing-skeleton" aria-busy="true" aria-label="Building your briefing">
      <div className="briefing-skeleton-verdict" />
      <div className="briefing-skeleton-line" />
      <div className="briefing-skeleton-row" />
      <div className="briefing-skeleton-row" />
      <div className="briefing-skeleton-row short" />
    </div>
  )
}
