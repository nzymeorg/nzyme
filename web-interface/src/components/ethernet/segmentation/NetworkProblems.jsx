import React from "react";
import {cidrToRange, isValidCIDR} from "../../../util/Tools";

const MAX_ASN = 4294967295;

// With addressToo, the text says that a single address is fine as well. It is accepted either way.
export const networkProblem = (typed, example, addressToo = false) => {
  const value = typed ? typed.trim() : "";
  if (value === "") {
    return null;
  }

  const range = cidrToRange(value);
  if (!range) {
    return (
      <span>
        Not {addressToo ? "an address or " : ""}a network in CIDR notation, like <code>{example}</code>.
      </span>
    )
  }

  if (!isValidCIDR(value)) {
    return (
      <span>
        The address is not the start of the network. Use <code>{range.from}/{value.split("/")[1]}</code>.
      </span>
    )
  }

  return null;
}

export const asnProblem = (typed) => {
  const value = typed ? String(typed).trim() : "";
  if (value === "") {
    return null;
  }

  return /^[0-9]+$/.test(value) && Number(value) >= 1 && Number(value) <= MAX_ASN
    ? null : "Not an autonomous system number. It is a number between 1 and " + MAX_ASN + ".";
}
