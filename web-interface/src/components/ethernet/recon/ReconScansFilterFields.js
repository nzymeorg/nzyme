import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const RECON_SCANS_FILTER_FIELDS = {
  type: { title: "Type", type: FILTER_TYPE.RECON_SCAN_TYPE },
  source_address: { title: "Scanner Address", type: FILTER_TYPE.IP_ADDRESS },
  source_mac: { title: "Scanner MAC", type: FILTER_TYPE.MAC_ADDRESS },
  source_tag: { title: "Scanner Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  target_address: { title: "Target Address", type: FILTER_TYPE.IP_ADDRESS },
  host_count: { title: "Host Count", type: FILTER_TYPE.NUMERIC },
  port_count: { title: "Port Count", type: FILTER_TYPE.NUMERIC },
  probe_count: { title: "Probe Count", type: FILTER_TYPE.NUMERIC },
  open_count: { title: "Answered Count", type: FILTER_TYPE.NUMERIC }
}
