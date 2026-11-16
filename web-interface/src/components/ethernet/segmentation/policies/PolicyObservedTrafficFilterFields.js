import {FILTER_TYPE} from "../../../shared/filtering/Filters";
import {FOLDED_PORT, VERDICTS} from "../SegmentationFormat";
import {sessionsOf} from "../overview/SegmentationOverviewTheme";

// A row that stands for many ports names none.
const port = (row) => (row.port === FOLDED_PORT ? null : row.port);

export const POLICY_OBSERVED_TRAFFIC_FILTER_FIELDS = {
  source: { title: "Source", type: FILTER_TYPE.STRING, value: (row) => row.source.label },
  destination: { title: "Destination", type: FILTER_TYPE.STRING, value: (row) => row.destination.label },
  l4_type: { title: "Type", type: FILTER_TYPE.L4_SESSION_TYPE, value: (row) => row.l4_type },
  port: { title: "Port", type: FILTER_TYPE.NUMERIC, value: port },
  protocol: { title: "Protocol", type: FILTER_TYPE.STRING, value: (row) => row.protocol },
  verdict: { title: "Verdict", type: FILTER_TYPE.SEGMENTATION_VERDICT, value: (row) => row.verdict },
  sessions: { title: "Sessions", type: FILTER_TYPE.NUMERIC, value: sessionsOf }
}

export const POLICY_OBSERVED_TRAFFIC_SORT_COLUMNS = {
  sessions: sessionsOf,
  source: (row) => row.source.label,
  destination: (row) => row.destination.label,
  port: port,
  connected: (row) => row.connected,
  rejected: (row) => row.rejected,
  no_reply: (row) => row.no_reply,
  sent: (row) => row.sent,
  last_seen: (row) => new Date(row.last_seen).getTime(),
  verdict: (row) => (VERDICTS[row.verdict] ? VERDICTS[row.verdict].title : row.verdict)
}
