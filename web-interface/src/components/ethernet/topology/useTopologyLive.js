import {useEffect, useRef, useState} from "react";
import {toast} from "react-toastify";
import {createLiveState, ingestLive, LIVE_DELAY_MS, LIVE_POLL_MS, LIVE_TICK_MS, liveFrame} from "./TopologyLive";

const FIRST_LOOKBACK_SECONDS = 60;
// A report of a tap can land this long after the activity it carries, or carry a clock that is this far behind.
const LATENESS_SECONDS = 30;
const REQUEST_TIMEOUT_MS = 20000;
const STALE_MS = 20000;

export default function useTopologyLive(key, enabled, load, options) {
  const [state, setState] = useState({key: null, frame: null});
  const loadRef = useRef(load);
  loadRef.current = load;
  const optionsRef = useRef(options);
  optionsRef.current = options;

  useEffect(() => {
    if (!enabled || key === null) {
      return undefined;
    }

    const live = createLiveState();
    let cancelled = false;
    let clockOffset = null;
    let previous = null;
    let lastSuccess = 0;
    let requestedAt = 0;
    let failing = false;

    const tick = () => {
      if (cancelled || clockOffset === null || document.hidden) return;

      const playhead = Date.now() + clockOffset - LIVE_DELAY_MS;
      // After a pause, like a tab in the background, what was missed is not replayed.
      const since = previous === null || playhead - previous > 3 * LIVE_TICK_MS ? playhead - LIVE_TICK_MS : previous;
      previous = playhead;

      const frame = liveFrame(live, playhead, since, optionsRef.current);
      frame.stale = Date.now() - lastSuccess > STALE_MS;
      setState({key: key, frame: frame});
    }

    const poll = () => {
      const now = Date.now();
      // The REST client reports no failure for a request without a response, so a request can only be given up on.
      if (cancelled || document.hidden || (requestedAt > 0 && now - requestedAt < REQUEST_TIMEOUT_MS)) return;
      requestedAt = now;

      const seconds = lastSuccess === 0 ? FIRST_LOOKBACK_SECONDS
          : Math.min(FIRST_LOOKBACK_SECONDS, Math.ceil((now - lastSuccess) / 1000) + LATENESS_SECONDS);

      // A response that was built a moment ago, like one that several screens share, says how long ago.
      loadRef.current(seconds, (response, ageMs = 0) => {
        if (cancelled) return;
        requestedAt = 0;
        failing = false;

        const first = clockOffset === null;
        lastSuccess = Date.now();
        // The timestamps of sessions are compared with the clock the node answers with, not with the browser's.
        clockOffset = Date.parse(response.now) + ageMs - lastSuccess;
        ingestLive(live, response);
        if (first) tick();
      }, (error) => {
        if (cancelled) return;
        requestedAt = 0;

        // Once per run of failures, not with every attempt.
        if (!failing && error && error.response) toast.error("REST call failed. (HTTP " + error.response.status + ")");
        failing = true;
      });
    }

    const onVisibilityChange = () => {
      if (!document.hidden) poll();
    }

    poll();
    const pollTimer = window.setInterval(poll, LIVE_POLL_MS);
    const tickTimer = window.setInterval(tick, LIVE_TICK_MS);
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      cancelled = true;
      window.clearInterval(pollTimer);
      window.clearInterval(tickTimer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      // The same key can come back, by turning live off and on again. Its frame from before is not live.
      setState({key: null, frame: null});
    };
  }, [key, enabled]);

  return enabled && state.key === key ? state.frame : null;
}
