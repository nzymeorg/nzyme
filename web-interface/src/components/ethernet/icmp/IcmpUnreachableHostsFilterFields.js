import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_UNREACHABLE_HOSTS_FILTER_FIELDS = {
  unreachable_host: { title: "Unreachable Host", type: FILTER_TYPE.IP_ADDRESS },
  source: { title: "Source", type: FILTER_TYPE.IP_ADDRESS },
  reporting_router: { title: "Reporting Router", type: FILTER_TYPE.IP_ADDRESS },
  protocol: { title: "Protocol", type: FILTER_TYPE.TRACEROUTE_PROTOCOL }
}
