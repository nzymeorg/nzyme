import {spoofingTypeToName} from "../../../ethernet/spoofing/SpoofingType";

export const ALLOWED_SPOOFING_TYPE_VALUES = ["ARP", "DHCP", "NAME_RESOLUTION", "DNS"];

export const SPOOFING_TYPE_SELECT_OPTIONS = ALLOWED_SPOOFING_TYPE_VALUES.map((type) => {
  return {name: spoofingTypeToName(type), value: type};
});
