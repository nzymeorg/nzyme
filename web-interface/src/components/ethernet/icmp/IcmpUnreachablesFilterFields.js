import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_UNREACHABLES_FILTER_FIELDS = {
  host: { title: "Host", type: FILTER_TYPE.IP_ADDRESS },
  source: { title: "Source", type: FILTER_TYPE.IP_ADDRESS },
  protocol: { title: "Protocol", type: FILTER_TYPE.TRACEROUTE_PROTOCOL },
  port: { title: "Port", type: FILTER_TYPE.NUMERIC }
}
