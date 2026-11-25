import {parseIPv6} from "./Tools";

const PREFIX = [0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0];

export const nat64EmbeddedAddress = (ip) => {
  if (!ip || !String(ip).toLowerCase().startsWith("64:ff9b:")) {
    return null;
  }

  const bytes = parseIPv6(String(ip).toLowerCase());
  if (!bytes || PREFIX.some((byte, i) => bytes[i] !== byte)) {
    return null;
  }

  return bytes.slice(12).join(".");
}
