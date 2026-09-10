import { useCallback, useRef } from "react";
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

export function timeRangeFromURLOrDefault(defaultRange, key = "timerange") {
  const queryParams = new URLSearchParams(window.location.search);
  const raw = queryParams.get("tr_" + key);
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