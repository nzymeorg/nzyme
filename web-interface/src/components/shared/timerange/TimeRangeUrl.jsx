import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Absolute, Named, Relative } from "./TimeRange";

export function serializeTimeRange(range) {
  if (!range) return null;
  return {
    type: range.type,
    ...(range.type === "relative" && { minutes: range.minutes }),
    ...(range.type === "absolute" && {
      from: range.from.toISOString(),
      to: range.to.toISOString(),
    }),
    ...(range.type === "named" && { name: range.name }),
  };
}

export function relativeLabel(minutes) {
  if (minutes % (60 * 24) === 0) {
    const days = minutes / (60 * 24);
    return `Last ${days} ${days === 1 ? "Day" : "Days"}`;
  }
  if (minutes % 60 === 0) {
    const hours = minutes / 60;
    return `Last ${hours} ${hours === 1 ? "Hour" : "Hours"}`;
  }
  return `Last ${minutes} ${minutes === 1 ? "Minute" : "Minutes"}`;
}

export function deserializeTimeRange(raw) {
  if (!raw) return null;
  switch (raw.type) {
    case "relative":
      return Relative(raw.minutes, relativeLabel(raw.minutes));
    case "absolute":
      return Absolute(new Date(raw.from), new Date(raw.to));
    case "named":
      return Named(raw.name);
    default:
      return null;
  }
}

function timeRangeFromRaw(raw, defaultRange, key) {
  if (raw) {
    try {
      const parsed = deserializeTimeRange(JSON.parse(raw));
      if (parsed) return parsed;
    } catch (e) {
      console.warn(`Failed to parse timerange "tr_${key}" from URL:`, e);
    }
  }
  return defaultRange;
}

export function timeRangeFromURLOrDefault(defaultRange, key = "timerange") {
  const queryParams = new URLSearchParams(window.location.search);
  return timeRangeFromRaw(queryParams.get("tr_" + key), defaultRange, key);
}

/*
 * Time range state that starts from the "tr_<key>" URL query parameter and follows later changes of that parameter.
 * With client-side navigation the parameter can change while the page stays mounted, so a plain useState initializer
 * would keep a stale range. The state is only replaced when the URL carries a different range than the current one,
 * which keeps the URL sync done by useTimeRangeUrlSync from causing a second render.
 */
export function useUrlTimeRange(defaultRange, key = "timerange") {
  const location = useLocation();
  const raw = new URLSearchParams(location.search).get("tr_" + key);

  const [timeRange, setTimeRange] = useState(() => timeRangeFromRaw(raw, defaultRange, key));
  const lastRaw = useRef(raw);

  useEffect(() => {
    if (lastRaw.current === raw) {
      return;
    }

    lastRaw.current = raw;

    setTimeRange((current) => {
      const currentRaw = current ? JSON.stringify(serializeTimeRange(current)) : null;
      return currentRaw === raw ? current : timeRangeFromRaw(raw, defaultRange, key);
    });
  }, [raw]);

  return [timeRange, setTimeRange];
}

export function useTimeRangeUrlSync(urlKey = "timerange", doNotPersist = false) {
  const navigate = useNavigate();
  const location = useLocation();
  const previousTimeRangeRef = useRef(null);

  return useCallback(
    (range) => {
      if (doNotPersist) return;

      const queryParams = new URLSearchParams(location.search);
      const serialized = JSON.stringify(serializeTimeRange(range));
      const key = "tr_" + urlKey;

      if (previousTimeRangeRef.current !== serialized) {
        previousTimeRangeRef.current = serialized;
        if (queryParams.get(key) !== serialized) {
          queryParams.set(key, serialized);
          navigate({ search: queryParams.toString() });
        }
      }
    },
    [navigate, location.search, urlKey, doNotPersist]
  );
}

export function useApplyTimeRange(setTimeRange, urlKey = "timerange", doNotPersist = false) {
  const syncTimeRangeToURL = useTimeRangeUrlSync(urlKey, doNotPersist);
  return useCallback(
    (range) => {
      if (setTimeRange) setTimeRange(range);
      syncTimeRangeToURL(range);
    },
    [setTimeRange, syncTimeRangeToURL]
  );
}