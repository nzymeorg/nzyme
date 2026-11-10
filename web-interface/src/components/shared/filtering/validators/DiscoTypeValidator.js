import {discoTypeToName} from "../../../dot11/disco/DiscoType";

export const ALLOWED_DISCO_TYPE_VALUES = ["DEAUTHENTICATION", "DISASSOCIATION"];

export const DISCO_TYPE_SELECT_OPTIONS = ALLOWED_DISCO_TYPE_VALUES.map((type) => {
  return {name: discoTypeToName(type), value: type};
});
