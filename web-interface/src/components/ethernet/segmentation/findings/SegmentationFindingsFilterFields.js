import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTATION_POLICY_FINDINGS_FILTER_FIELDS = {
  finding_class: { title: "Class", type: FILTER_TYPE.SEGMENTATION_FINDING_CLASS },
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  source_asn: { title: "Source ASN", type: FILTER_TYPE.NUMERIC },
  source_country_code: { title: "Source Country Code", type: FILTER_TYPE.STRING },
  target_address: { title: "Target Address", type: FILTER_TYPE.IP_ADDRESS },
  target_port: { title: "Target Port", type: FILTER_TYPE.NUMERIC },
  audit: { title: "Audit", type: FILTER_TYPE.BOOLEAN },
  session_count: { title: "Sessions", type: FILTER_TYPE.NUMERIC },
  target_count: { title: "Targets", type: FILTER_TYPE.NUMERIC }
}

export const SEGMENTATION_FINDINGS_FILTER_FIELDS = {
  policy_name: { title: "Policy", type: FILTER_TYPE.STRING },
  ...SEGMENTATION_POLICY_FINDINGS_FILTER_FIELDS
}
