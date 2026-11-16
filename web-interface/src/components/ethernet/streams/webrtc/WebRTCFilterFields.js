import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const WEBRTC_FILTER_FIELDS = {
  id: { title: "ID", type: FILTER_TYPE.STRING },
  source_address: { title: "Peer A Address", type: FILTER_TYPE.IP_ADDRESS },
  source_mac: { title: "Peer A MAC Address", type: FILTER_TYPE.STRING },
  source_tag: { title: "Peer A Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  destination_address: { title: "Peer B IP Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_mac: { title: "Peer B MAC Address", type: FILTER_TYPE.STRING },
  destination_tag: { title: "Peer B Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  has_rtp: { title: "Has RTP", type: FILTER_TYPE.BOOLEAN },
  has_dtls: { title: "Has DTLS", type: FILTER_TYPE.BOOLEAN },
  has_audio: { title: "Has Audio", type: FILTER_TYPE.BOOLEAN },
  has_video: { title: "Has Video", type: FILTER_TYPE.BOOLEAN },
  active: { title: "Active", type: FILTER_TYPE.BOOLEAN },
  stream_count: { title: "Stream Count", type: FILTER_TYPE.NUMERIC },
  bytes_exchanged: { title: "Bytes Exchanged", type: FILTER_TYPE.NUMERIC },
  duration_ms: { title: "Duration (Milliseconds)", type: FILTER_TYPE.NUMERIC },
}