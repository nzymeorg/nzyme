export const LIVE_WINDOW_MS = 30000;
// How far the view runs behind. Taps report every ten seconds, so what happened more recently is not complete yet.
export const LIVE_DELAY_MS = 20000;
export const LIVE_POLL_MS = 5000;
export const LIVE_TICK_MS = 1000;
export const LIVE_SESSION_LIMIT = 5000;

const HORIZON_MS = LIVE_WINDOW_MS + LIVE_DELAY_MS;
// A session that went quiet is remembered for a while, so that its next bytes can be told from its earlier ones.
const IDLE_KEEP_MS = 300000;
const MAX_SESSIONS = 50000;
// The time between two reports of a tap, with some slack.
const REPORT_MS = 15000;
const MAX_PULSES = 150;
const MAX_PULSES_PER_LINK = 3;
const SCALE_DECAY = 0.97;

export function createLiveState() {
  return {
    sessions: new Map(),
    nodes: new Map(),
    sites: [],
    unplacedTaps: [],
    mergeTaps: false,
    sessionLimit: 0,
    limitedAt: null,
    coveredFrom: null,
    coveredTo: null,
    playhead: null,
    scale: {bytes: 0, sessions: 0, peers: 0}
  };
}

export function ingestLive(state, response) {
  const now = Date.parse(response.now);
  const from = now - response.seconds * 1000;

  // Since when no report can have been missed. A response that was cut short, or that does not reach back to the
  // one before it, starts over.
  if (response.limited) {
    state.limitedAt = now;
    state.coveredFrom = now;
  } else if (state.coveredTo === null || from > state.coveredTo) {
    state.coveredFrom = from;
  }
  state.coveredTo = now;

  state.mergeTaps = response.merge_taps;
  state.sessionLimit = response.limit;
  state.sites = response.sites;
  state.unplacedTaps = response.unplaced_taps;
  response.nodes.forEach((n) => state.nodes.set(n.id, n));

  response.sessions.forEach((s) => {
    const last = Date.parse(s.most_recent_segment_time);
    const start = Math.min(Date.parse(s.start_time), last);
    if (!Number.isFinite(start) || !Number.isFinite(last)) return;

    const key = s.tap_uuid + "|" + s.l4_type + "|" + s.session_key;
    let session = state.sessions.get(key);
    if (!session) {
      // All bytes of a young session were sent in the time it is shown for. Of an older one, only that it was
      // active since the report before this one is known, not how much of its total is recent: it counts from
      // its next report on.
      const young = start >= last - HORIZON_MS;

      session = {
        tap: s.tap_uuid, tcp: s.l4_type === "TCP", start: start, tx: 0, rx: 0,
        pair: s.source_address + "|" + s.destination_address + "|" + s.l4_type + "|" + s.destination_port,
        // Reported after the view passed its start.
        late: state.playhead !== null && start <= state.playhead,
        segments: [{
          from: young ? start : last - REPORT_MS, to: last, tx: young ? s.bytes_tx : 0, rx: young ? s.bytes_rx : 0
        }]
      };
      state.sessions.set(key, session);
    } else if (last > session.last) {
      // A session that was quiet under watch gained its bytes since the report before this one, not since it was
      // last active.
      const watched = session.last >= state.coveredFrom;

      session.segments.push({
        from: watched ? Math.max(session.last, last - REPORT_MS) : session.last, to: last,
        tx: Math.max(0, s.bytes_tx - session.tx), rx: Math.max(0, s.bytes_rx - session.rx)
      });
    } else {
      return;
    }

    session.source = s.source;
    session.target = s.target;
    session.last = last;
    session.tx = Math.max(session.tx, s.bytes_tx);
    session.rx = Math.max(session.rx, s.bytes_rx);
    session.latitude = s.latitude;
    session.longitude = s.longitude;
    session.city = s.city;
    session.countryCode = s.country_code;
  });
}

const emptySum = () => ({sessions: 0, tcp: 0, udp: 0, tx: 0, rx: 0, first: Infinity, last: -Infinity, starts: []});

const addSum = (sum, other) => {
  sum.sessions += other.sessions;
  sum.tcp += other.tcp;
  sum.udp += other.udp;
  sum.tx += other.tx;
  sum.rx += other.rx;
  sum.first = Math.min(sum.first, other.first);
  sum.last = Math.max(sum.last, other.last);
  other.starts.forEach((s) => sum.starts.push(s));
}

const sumOf = (map, key, create) => {
  let sum = map.get(key);
  if (!sum) {
    sum = {...emptySum(), ...create()};
    map.set(key, sum);
  }
  return sum;
}

/*
 * Taps that recorded the same traffic: like the topology queries of the node, per pair of addresses and port the
 * most that any one tap saw counts, for sessions and for the bytes of each direction on their own.
 */
const mostSeen = (taps) => {
  const merged = emptySum();
  let starting = null;
  let startingTap = null;

  taps.forEach((sum, tap) => {
    merged.sessions = Math.max(merged.sessions, sum.sessions);
    merged.tcp = Math.max(merged.tcp, sum.tcp);
    merged.udp = Math.max(merged.udp, sum.udp);
    merged.tx = Math.max(merged.tx, sum.tx);
    merged.rx = Math.max(merged.rx, sum.rx);
    merged.first = Math.min(merged.first, sum.first);
    merged.last = Math.max(merged.last, sum.last);

    if (starting === null || sum.starts.length > starting.length
        || (sum.starts.length === starting.length && tap < startingTap)) {
      starting = sum.starts;
      startingTap = tap;
    }
  });

  merged.starts = starting || [];
  return merged;
}

const iso = (ms) => new Date(ms).toISOString();

const sessionActivity = (session, from, to) => {
  let tx = 0, rx = 0, last = -Infinity;

  for (let i = 0; i < session.segments.length; i++) {
    const g = session.segments[i];
    if (g.to <= from || g.from > to) continue;

    if (g.to === g.from) {
      tx += g.tx;
      rx += g.rx;
      last = Math.max(last, g.to);
      continue;
    }

    const end = Math.min(g.to, to);
    const overlap = end - Math.max(g.from, from);
    if (overlap <= 0) continue;

    const share = overlap / (g.to - g.from);
    tx += g.tx * share;
    rx += g.rx * share;
    last = Math.max(last, end);
  }

  return last === -Infinity ? null : {tx: tx, rx: rx, last: last};
}

// The window that ends at the playhead, in the shape of the links and geo responses of the API.
export function liveFrame(state, playhead, previous, options) {
  const from = playhead - LIVE_WINDOW_MS;
  const withGeo = Boolean(options.geo);
  state.playhead = playhead;

  const links = new Map();
  const pairs = new Map();
  const flows = new Map();
  const referenced = new Set();
  const crowded = state.sessions.size > MAX_SESSIONS;

  state.sessions.forEach((session, key) => {
    if (session.segments.length > 0) session.segments = session.segments.filter((g) => g.to > from);
    if (session.segments.length === 0 && (crowded || session.last <= from - IDLE_KEEP_MS)) {
      state.sessions.delete(key);
      return;
    }

    referenced.add(session.source);
    referenced.add(session.target);

    const activity = sessionActivity(session, from, playhead);
    if (!activity) return;

    const starts = session.late || (session.start > previous && session.start <= playhead);
    session.late = false;

    const one = {
      sessions: 1, tcp: session.tcp ? 1 : 0, udp: session.tcp ? 0 : 1, tx: activity.tx, rx: activity.rx,
      first: Math.max(session.start, from), last: activity.last,
      starts: starts ? [Math.max(0, session.start - previous)] : []
    };

    const link = () => ({source: session.source, target: session.target});
    addSum(sumOf(sumOf(pairs, session.pair, () => ({...link(), taps: new Map()})).taps, session.tap, () => ({})), one);
    if (!state.mergeTaps) {
      addSum(sumOf(links, session.source + ">" + session.target, link), one);
    }

    if (withGeo) {
      const source = state.nodes.get(session.source);
      const target = state.nodes.get(session.target);
      if (source && target && (source.type === "INTERNAL") !== (target.type === "INTERNAL")) {
        const outbound = source.type === "INTERNAL";
        const located = typeof session.latitude === "number" && typeof session.longitude === "number";
        const place = located ? session.latitude + "," + session.longitude : "cc:" + (session.countryCode || "-");

        const flow = sumOf(flows, session.tap + "|" + outbound + "|" + place, () => ({
          tap: session.tap, outbound: outbound, latitude: located ? session.latitude : null,
          longitude: located ? session.longitude : null, city: null, countryCode: null
        }));
        // Seen from the tap: what the internal side sent and received.
        addSum(flow, {...one, tx: outbound ? one.tx : one.rx, rx: outbound ? one.rx : one.tx});
        if (session.city && (!flow.city || session.city > flow.city)) flow.city = session.city;
        if (session.countryCode && (!flow.countryCode || session.countryCode > flow.countryCode)) {
          flow.countryCode = session.countryCode;
        }
      }
    }
  });

  state.nodes.forEach((node, id) => {
    if (!referenced.has(id)) state.nodes.delete(id);
  });

  // The totals count shared traffic once also where the links do not: grouped by tap.
  const total = {sessions: 0, bytes: 0};
  pairs.forEach((pair) => {
    const merged = mostSeen(pair.taps);
    total.sessions += merged.sessions;
    total.bytes += merged.tx + merged.rx;
    if (state.mergeTaps) {
      addSum(sumOf(links, pair.source + ">" + pair.target, () => ({source: pair.source, target: pair.target})), merged);
    }
  });

  const series = {from: iso(from), bucket_size_ms: LIVE_WINDOW_MS, count: 1};
  const point = (sum) => [[0, sum.sessions, Math.round(sum.tx), Math.round(sum.rx), sum.sessions]];
  const byBytes = (a, b) => (b.tx + b.rx) - (a.tx + a.rx) || b.sessions - a.sessions;

  const all = Array.from(links.values())
      .filter((l) => state.nodes.has(l.source) && state.nodes.has(l.target))
      .sort((a, b) => byBytes(a, b) || (a.source + a.target < b.source + b.target ? -1 : 1));
  const shown = all.slice(0, options.limit);

  const nodeSums = new Map();
  const nodeSum = (id) => {
    let sum = nodeSums.get(id);
    if (!sum) {
      sum = {bytes: 0, sessions: 0, peers: new Set()};
      nodeSums.set(id, sum);
    }
    return sum;
  }

  const pulses = [];
  shown.forEach((l) => {
    const source = nodeSum(l.source);
    source.bytes += l.tx + l.rx;
    source.sessions += l.sessions;
    if (l.source !== l.target) {
      const target = nodeSum(l.target);
      target.bytes += l.tx + l.rx;
      target.sessions += l.sessions;
      source.peers.add(l.target);
      target.peers.add(l.source);

      l.starts.sort((a, b) => a - b).slice(0, MAX_PULSES_PER_LINK).forEach((delay) => {
        if (pulses.length < MAX_PULSES) pulses.push({source: l.source, target: l.target, delay: delay});
      });
    }
  });

  const largest = {bytes: 0, sessions: 0, peers: 0};
  nodeSums.forEach((sum) => {
    largest.bytes = Math.max(largest.bytes, sum.bytes);
    largest.sessions = Math.max(largest.sessions, sum.sessions);
    largest.peers = Math.max(largest.peers, sum.peers.size);
  });
  // Sizes are relative to the busiest node of the recent past, so that one quiet second does not blow all nodes up.
  Object.keys(largest).forEach((k) => {
    state.scale[k] = Math.max(largest[k], state.scale[k] * SCALE_DECAY);
  });

  const active = new Set();
  all.forEach((l) => {
    active.add(l.source);
    active.add(l.target);
  });
  let internal = 0;
  active.forEach((id) => {
    if (state.nodes.get(id).type === "INTERNAL") internal++;
  });

  const frame = {
    playhead: playhead,
    links: {
      nodes: Array.from(nodeSums.keys()).map((id) => state.nodes.get(id)),
      services: [],
      links: shown.map((l) => ({
        source: l.source, target: l.target, service: null, sessions: l.sessions, sessions_tcp: l.tcp,
        sessions_udp: l.udp, bytes_tx: Math.round(l.tx), bytes_rx: Math.round(l.rx), first_seen: iso(l.first),
        last_seen: iso(l.last), series: point(l)
      })),
      total_links: all.length,
      total_sessions: all.reduce((sum, l) => sum + l.sessions, 0),
      total_bytes: Math.round(all.reduce((sum, l) => sum + l.tx + l.rx, 0)),
      limit: options.limit,
      series: series
    },
    geo: null,
    pulses: pulses,
    scale: {...state.scale},
    summary: {
      internal: internal, external: active.size - internal, connections: all.length, sessions: total.sessions,
      bytes: Math.round(total.bytes)
    },
    // Sessions that a response left out can be missing for as long as the window reaches back to it.
    limited: state.limitedAt !== null && state.limitedAt > from,
    sessionLimit: state.sessionLimit
  };

  if (withGeo) {
    const siteOfTap = new Map();
    state.sites.forEach((site) => site.taps.forEach((tap) => siteOfTap.set(tap.uuid, site.id)));

    const allFlows = Array.from(flows.values()).sort(byBytes);
    frame.geo = {
      sites: state.sites,
      unplaced_taps: state.unplacedTaps,
      flows: allFlows.slice(0, options.flowLimit).map((f) => ({
        site: siteOfTap.get(f.tap) || null, tap_uuid: f.tap, direction: f.outbound ? "OUTBOUND" : "INBOUND",
        latitude: f.latitude, longitude: f.longitude, city: f.city, country_code: f.countryCode,
        sessions: f.sessions, bytes_tx: Math.round(f.tx), bytes_rx: Math.round(f.rx), first_seen: iso(f.first),
        last_seen: iso(f.last), series: point(f)
      })),
      total_flows: allFlows.length,
      total_sessions: allFlows.reduce((sum, f) => sum + f.sessions, 0),
      total_bytes: Math.round(allFlows.reduce((sum, f) => sum + f.tx + f.rx, 0)),
      limit: options.flowLimit,
      series: series
    };
  }

  return frame;
}
