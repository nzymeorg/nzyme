import {useCallback, useContext, useEffect, useRef, useState} from "react";
import SearchService from "../../services/SearchService";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import {serializeTimeRange} from "../shared/timerange/TimeRangeUrl";
import {UserContext} from "../../App";
import {MINIMUM_QUERY_LENGTH, normalizedQuery} from "./searchQuery";

const searchService = new SearchService();

// The node limits how many scans of recorded data run at once. One search stays well below that.
const RECORDED_AT_ONCE = 3;

// A busy node is asked again after a pause. The requests of a query the user typed past can still run there.
const BUSY_RETRIES = 2;
const BUSY_RETRY_MS = 2000;

/*
 * Responses are kept for a short time, so typing a query again or deleting a character does not ask the node
 * again. Keys are built as JSON: a query is free text and must not be able to look like another part of a key.
 */
const CACHE_SECONDS = 60;
const CACHE_ENTRIES = 300;
const cache = new Map();

const cached = (key) => {
  const entry = cache.get(key);
  if (!entry) {
    return null;
  }

  if (Date.now() - entry.at > CACHE_SECONDS * 1000) {
    cache.delete(key);
    return null;
  }

  return entry.value;
}

const remember = (key, value) => {
  cache.delete(key);
  cache.set(key, {at: Date.now(), value: value});

  if (cache.size > CACHE_ENTRIES) {
    cache.delete(cache.keys().next().value);
  }
}

const IDLE = {query: "", status: "idle", interpretations: [], groups: {}, pending: []};

// A time range from a hand-edited URL can hold dates that are none. It then counts as no time range.
export const serializedRange = (timeRange) => {
  try {
    return timeRange ? serializeTimeRange(timeRange) : null;
  } catch {
    return null;
  }
}

/*
 * Runs a search in two steps: one request for everything that is quick, then one request per source of recorded
 * network data, each of which fills in its group as it arrives. A new query abandons the requests of the one
 * before it. A changed attempt runs the same query again.
 */
export default function useSearch(query, {limit = 5, timeRange = null, delay = 0, recordedDelay = 0, enabled = true, attempt = 0} = {}) {
  const user = useContext(UserContext);
  const [organizationId, tenantId] = useSelectedTenant();
  const [state, setState] = useState(IDLE);
  const run = useRef(0);
  const controller = useRef(null);
  const lastAttempt = useRef(attempt);
  const latest = useRef(state);

  useEffect(() => {
    latest.current = state;
  }, [state]);

  const normalized = normalizedQuery(query);

  // The serialized range is what the node keys its cache by, so it is sent without the label of a preset.
  const sentRange = serializedRange(timeRange);
  const scope = [user ? user.id : null, organizationId, tenantId, limit, sentRange, normalized.toLowerCase()];
  const responseKey = JSON.stringify(["search"].concat(scope));
  const groupKey = (source) => JSON.stringify(["source", source].concat(scope));

  useEffect(() => {
    run.current += 1;
    const current = run.current;

    if (controller.current) {
      controller.current.abort();
      controller.current = null;
    }

    if (!enabled || normalized.length < MINIMUM_QUERY_LENGTH) {
      setState(IDLE);
      return;
    }

    const alive = () => run.current === current;
    const timers = [];

    // Another attempt at the same query asks the node again, whatever is kept here.
    const retry = lastAttempt.current !== attempt;
    lastAttempt.current = attempt;
    const kept = (key) => retry ? null : cached(key);

    const addGroup = (group) => {
      if (!alive()) {
        return;
      }

      setState((previous) => ({
        ...previous,
        groups: {...previous.groups, [group.source]: group},
        pending: previous.pending.filter((source) => source.source !== group.source)
      }));
    }

    const failedGroup = (source) => ({
      source: source.source, title: source.title, position: source.position, tier: "RECORDED", status: "ERROR",
      hits: [], has_more: false, offset: 0, limit: limit
    });

    const start = (abort) => {
      const apply = (response) => {
        if (!alive()) {
          return;
        }

        const groups = {};
        response.groups.forEach((group) => { groups[group.source] = group; });

        const waiting = [];
        response.pending.forEach((source) => {
          const known = kept(groupKey(source.source));
          if (known) {
            groups[source.source] = known;
          } else {
            waiting.push(source);
          }
        });

        setState({
          query: normalized,
          status: "done",
          interpretations: response.interpretations,
          groups: groups,
          pending: waiting.slice()
        });

        let running = 0;
        const next = () => {
          while (alive() && running < RECORDED_AT_ONCE && waiting.length > 0) {
            const source = waiting.shift();
            running += 1;

            const done = (group) => {
              running -= 1;
              addGroup(group);
              next();
            }

            request(source, BUSY_RETRIES, done);
          }
        }

        const request = (source, retries, done) => {
          searchService.searchSource(source.source, normalized, organizationId, tenantId, limit, 0, sentRange,
            abort.signal, (group) => {
              if (group.status === "BUSY" && retries > 0 && alive()) {
                timers.push(setTimeout(() => request(source, retries - 1, done), BUSY_RETRY_MS));
                return;
              }

              if (group.status === "OK") {
                remember(groupKey(source.source), group);
              }
              done(group);
            }, () => done(failedGroup(source)));
        }

        // Scans of recorded data are what costs the node the most. They wait until typing has settled.
        timers.push(setTimeout(next, recordedDelay));
      }

      const known = kept(responseKey);
      if (known) {
        apply(known);
        return;
      }

      searchService.search(normalized, organizationId, tenantId, limit, sentRange, abort.signal, (response) => {
        if (response.groups.every((group) => group.status === "OK")) {
          remember(responseKey, response);
        }
        apply(response);
      }, () => {
        if (alive()) {
          setState({...IDLE, query: normalized, status: "failed"});
        }
      });
    }

    const abort = new AbortController();
    controller.current = abort;

    const stop = () => {
      timers.forEach(clearTimeout);
      abort.abort();
    }

    // What is cached shows without the delay, and without a loading state in between.
    if (kept(responseKey)) {
      start(abort);
      return stop;
    }

    setState((previous) => ({...previous, status: "loading", pending: []}));
    timers.push(setTimeout(() => start(abort), delay));

    return stop;
  }, [responseKey, enabled, attempt]);

  const loadMore = useCallback((source) => {
    const shown = latest.current;
    const group = shown.groups[source];

    // While another query loads, the groups on screen still belong to the one before it.
    if (!group || group.loading_more || shown.status !== "done" || shown.query !== normalized) {
      return;
    }

    const current = run.current;
    const patch = (changes) => setState((previous) => {
      const known = previous.groups[source];
      if (!known || run.current !== current) {
        return previous;
      }

      return {...previous, groups: {...previous.groups, [source]: {...known, ...changes(known)}}};
    });

    patch(() => ({loading_more: true, more_failed: false}));

    searchService.searchSource(source, normalized, organizationId, tenantId, limit, group.hits.length, sentRange,
      controller.current ? controller.current.signal : undefined,
      (more) => patch((known) => more.status === "OK"
        ? {hits: known.hits.concat(more.hits), has_more: more.has_more, limited: more.limited, loading_more: false}
        : {more_failed: true, loading_more: false}),
      () => patch(() => ({more_failed: true, loading_more: false})));
  }, [responseKey]);

  return {...state, loadMore: loadMore};
}

/*
 * Groups in the order they are shown: what was quick first, best matches on top, then recorded network data in a
 * fixed order. Groups of recorded data arrive one by one, and they must not push around what is already there.
 */
export function orderedGroups(groups) {
  const all = Object.values(groups);
  const best = (group) => group.hits.reduce((max, hit) => Math.max(max, hit.score), 0);

  const instant = all.filter((group) => group.tier === "INSTANT")
    .sort((a, b) => best(b) - best(a) || a.position - b.position);
  const recorded = all.filter((group) => group.tier !== "INSTANT")
    .sort((a, b) => a.position - b.position);

  return instant.concat(recorded);
}
