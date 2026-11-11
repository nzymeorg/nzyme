import React from "react";

const OPTION_NAMES = {
  mss: "maximum segment size",
  sok: "SACK permitted",
  ts: "timestamps",
  nop: "no-op",
  ws: "window scale",
  eol: "end of options",
  sack: "SACK",
  30: "multipath TCP"
};

const QUIRKS = {
  ts0: "timestamp option present with a zero value",
  ecr: "non-zero timestamp echo in the opening segment",
  id0: "zero IP ID although DF is set",
  bad: "options could not be read to the end"
};

export function describeTcpFingerprint(value) {
  if (!value) {
    return null;
  }

  const parts = value.split(":");
  if (parts.length < 5) {
    return value;
  }

  const lines = [];
  lines.push("Stack | Link (MSS, window scale) | Application (window, TOS)");
  lines.push("");
  lines.push("Initial TTL " + parts[0]);
  lines.push("Don't Fragment " + (parts[1] === "1" ? "set" : "not set"));
  lines.push("ECN " + (parts[2] === "1" ? "negotiated" : "not negotiated"));
  lines.push("Options: " + (parts[3] === "" ? "none" : parts[3].split(",").map((o) => OPTION_NAMES[o] ? OPTION_NAMES[o] : o).join(", ")));
  lines.push("Quirks: " + (parts[4] === "-" ? "none" : parts[4].split(",").map((q) => QUIRKS[q] ? QUIRKS[q] : q).join(", ")));

  if (parts.length >= 7) {
    lines.push("MSS " + (parts[5] === "0" ? "absent" : parts[5]));
    lines.push("Window scale " + (parts[6] === "-" ? "absent" : parts[6]));
  }

  if (parts.length >= 9) {
    const window = parts[7];
    lines.push("Window " + (window === "max" ? "65535" : window.indexOf("mss*") === 0 ? window.substring(4) + " times the MSS" : window));
    lines.push("TOS " + parts[8]);
  }

  return lines.join("\n");
}

const FIELD_NAMES = ["initial TTL", "Don't Fragment bit", "ECN", "option order", "quirks", "MSS", "window scale"];

const hasTimestamps = (options) => (options || "").split(",").indexOf("ts") !== -1;

const withoutTimestamps = (options) => (options || "").split(",")
  .filter((o) => o !== "ts" && o !== "nop" && o !== "eol").join(",");

const withoutTimestampQuirks = (quirks) => (quirks || "").split(",")
  .filter((q) => q !== "ts0" && q !== "ecr" && q !== "-").join(",");

export function describeStackDifference(value, others) {
  const parts = value.split(":");
  const differing = [];

  others.filter((o) => o !== value).forEach((other) => {
    const otherParts = other.split(":");

    const timestampsOnly = hasTimestamps(parts[3]) !== hasTimestamps(otherParts[3])
      && withoutTimestamps(parts[3]) === withoutTimestamps(otherParts[3]);

    FIELD_NAMES.forEach((fieldName, i) => {
      if (parts[i] === otherParts[i]) {
        return;
      }

      if (timestampsOnly && i === 4 && withoutTimestampQuirks(parts[4]) === withoutTimestampQuirks(otherParts[4])) {
        return;
      }

      const name = timestampsOnly && i === 3 ? "timestamps" : fieldName;
      if (differing.indexOf(name) === -1) {
        differing.push(name);
      }
    });
  });

  const them = others.length > 2 ? "the other stacks" : "the other stack";

  if (differing.length === 0) {
    return "Identical to " + them + ".";
  }

  const list = differing.length === 1 ? differing[0]
    : differing.slice(0, -1).join(", ") + " and " + differing[differing.length - 1];

  const onlyLink = differing.every((d) => d === "MSS" || d === "window scale");
  const onlyEcn = differing.length === 1 && differing[0] === "ECN";
  const onlyTimestamps = differing.length === 1 && differing[0] === "timestamps";
  const onlyWeak = differing.every((d) => d === "MSS" || d === "window scale" || d === "ECN" || d === "timestamps");

  if (onlyLink) {
    return "Differs from " + them + " in " + list + ", which follow the link, not the operating system.";
  }

  if (onlyEcn) {
    return "Differs from " + them + " in ECN only, which some devices decide per connection. This is likely one device.";
  }

  if (onlyTimestamps) {
    return "Differs from " + them + " in timestamps only, which Windows applications can turn on per connection. This is likely one device.";
  }

  if (onlyWeak) {
    return "Differs from " + them + " in " + list + ". None of these fields tell devices apart on their own.";
  }

  return "Differs from " + them + " in " + list + ".";
}

export default function TcpFingerprint({value, id, compact}) {

  if (!value) {
    return <span className="text-muted">n/a</span>;
  }

  if (compact && id) {
    return <span className="machine-data" title={value + "\n\n" + describeTcpFingerprint(value)} style={{cursor: "help"}}>{id}</span>;
  }

  // Each layer is a prefix of the next, so the colors show where two values stop matching.
  const parts = value.split(":");
  const segments = [
    {className: "tcp-fp-stack", parts: parts.slice(0, 5)},
    {className: "tcp-fp-link", parts: parts.slice(5, 7)},
    {className: "tcp-fp-app", parts: parts.slice(7, 9)}
  ].filter((seg) => seg.parts.length > 0);

  return (
    <span className="machine-data" title={describeTcpFingerprint(value)} style={{cursor: "help"}}>
      {segments.map((seg, i) => (
        <React.Fragment key={seg.className}>
          {i > 0 ? ":" : null}
          <span className={seg.className}>{seg.parts.join(":")}</span>
        </React.Fragment>
      ))}
    </span>
  );

}
