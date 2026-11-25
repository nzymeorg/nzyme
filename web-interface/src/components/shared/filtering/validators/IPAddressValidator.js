import {isValidCIDR} from "../../../../util/Tools";

export default function validateIPAddressValid(value) {
  if (!value) {
    return false;
  }

  const address = value.trim();

  return !address.includes("/") && isValidCIDR(address);
}
