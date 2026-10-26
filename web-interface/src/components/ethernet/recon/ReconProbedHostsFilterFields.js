import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const RECON_PROBED_HOSTS_FILTER_FIELDS = {
  address: { title: "Host Address", type: FILTER_TYPE.IP_ADDRESS },
  source_count: { title: "Probing Devices", type: FILTER_TYPE.NUMERIC },
  port_count: { title: "Probed Ports", type: FILTER_TYPE.NUMERIC },
  failed_port_count: { title: "Unanswered Ports", type: FILTER_TYPE.NUMERIC }
}

export const RECON_PROBED_HOSTS_DEFAULT_FILTERS = {
  source_count: [{field: "source_count", operator: "greater_than", value: "1"}],
  failed_port_count: [{field: "failed_port_count", operator: "greater_than", value: "0"}]
}
