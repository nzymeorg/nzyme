import {isValidCIDR} from "../../../../util/Tools";

export default function validateCIDRValid(value) {
  if (!value) {
    return false;
  }

  const cidr = value.trim();

  return cidr.includes("/") && isValidCIDR(cidr);
}
