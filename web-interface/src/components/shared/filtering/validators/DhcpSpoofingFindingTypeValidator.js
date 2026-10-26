import {dhcpSpoofingFindingTypeToName} from "../../../ethernet/spoofing/DhcpSpoofingFindingType";

export const ALLOWED_DHCP_SPOOFING_FINDING_TYPE_VALUES = ["ROGUE_SERVER", "PARAMETERS_CHANGED"];

export const DHCP_SPOOFING_FINDING_TYPE_SELECT_OPTIONS = ALLOWED_DHCP_SPOOFING_FINDING_TYPE_VALUES.map((type) => {
  return {name: dhcpSpoofingFindingTypeToName(type), value: type};
});
