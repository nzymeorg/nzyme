import numeral from "numeral";

const count = (value, singular, plural) => {
  return numeral(value).format("0,0") + " " + (value === 1 ? singular : plural);
}

const client = (a) => a.client ? a.client.mac.address : null;
const framesToBssid = (a) => a.frame_count - a.frames_from_bssid;

const discoFrames = (a, value) => {
  const deauthentication = a.frame_types.includes("DEAUTHENTICATION");
  const disassociation = a.frame_types.includes("DISASSOCIATION");

  if (deauthentication && !disassociation) {
    return count(value, "deauthentication frame", "deauthentication frames");
  }

  if (disassociation && !deauthentication) {
    return count(value, "disassociation frame", "disassociation frames");
  }

  return count(value, "disconnection frame", "disconnection frames");
}

const direction = (a) => {
  if (!client(a)) {
    return "in the name of the access point to all of its clients";
  }

  if (framesToBssid(a) === 0) {
    return "in the name of the access point to the client";
  }

  if (a.frames_from_bssid === 0) {
    return "in the name of the client to the access point";
  }

  return "between the access point and the client, in both directions";
}

const dbm = (value) => numeral(value).format("0") + " dBm";

const signalDifference = (a) => {
  const difference = a.signal_strength - a.reference_signal_strength;
  return numeral(Math.abs(difference)).format("0") + " dB " + (difference < 0 ? "weaker" : "stronger");
}

const eapolFrames = (a) => {
  const start = a.frame_types.includes("EAPOL_START");
  const logoff = a.frame_types.includes("EAPOL_LOGOFF");

  if (start && logoff) {
    return "EAPOL start and logoff frames";
  }

  return logoff ? "EAPOL logoff frames" : "EAPOL start frames";
}

export const DISCO_ANOMALY_TYPES = {

  UNPROTECTED_FRAME: {
    name: "Unprotected Frame on Protected Network",
    helpLink: "https://go.nzyme.org/disco-anomalies-unprotected-frame",
    summary: (a) => count(a.frame_count, "unprotected frame", "unprotected frames")
        + (client(a) ? "" : " to all clients"),
    observed: (a) => `The tap recorded ${discoFrames(a, a.frame_count)} without protection, sent ${direction(a)}.`,
    meaning: "The network of this access point requires protected management frames. A client and its access "
        + "point encrypt the disconnection frames they send each other, and frames to all clients carry an integrity "
        + "check. Clients ignore a frame that has neither, so the connection stays up. The frame still shows that "
        + "someone tried to end it.",
    falsePositives: [
      "A station removed its keys before it sent the frame.",
      "A connection attempt failed before keys were in place and the tap did not record its authentication or "
          + "handshake frames.",
      "With the exemption for reason codes 6 and 7 turned off: an access point that no longer knows a client."
    ]
  },

  MIRRORED_FRAMES: {
    name: "Frames in Both Directions",
    helpLink: "https://go.nzyme.org/disco-anomalies-both-directions",
    summary: (a) => `${numeral(a.frames_from_bssid).format("0,0")} as the access point, `
        + `${numeral(framesToBssid(a)).format("0,0")} as the client`,
    observed: (a) => `The tap recorded ${discoFrames(a, a.frames_from_bssid)} in the name of the access point `
        + `to the client, and ${numeral(framesToBssid(a)).format("0,0")} in the name of the client to the access `
        + `point, within seconds of each other.`,
    meaning: "One side ends a connection. Attack tools send the frame to both sides at once, so that the "
        + "connection ends even if one of them ignores it.",
    falsePositives: [
      "A client answered the disconnection frame of its access point with one of its own."
    ]
  },

  REPEATED_FRAMES: {
    name: "Repeated Transmissions",
    helpLink: "https://go.nzyme.org/disco-anomalies-repeated-transmissions",
    summary: (a) => count(a.frame_count, "frame", "frames")
        + (a.peak > 1 ? `, up to ${numeral(a.peak).format("0,0")} sequence numbers in ten seconds` : ", one sequence number"),
    observed: (a) => `The tap recorded ${discoFrames(a, a.frame_count)} sent ${direction(a)}. `
        + (a.peak > 1
            ? `Up to ${numeral(a.peak).format("0,0")} of them within ten seconds had different sequence numbers.`
            : "They all had the same sequence number."),
    meaning: "A station sends a disconnection frame once and repeats it with the same sequence number until it "
        + "is acknowledged, a handful of times at most. Frames with new sequence numbers are separate "
        + "transmissions. Many of them, or many repetitions of one, is what a tool does that keeps a client off "
        + "the network or forces it to reconnect.",
    falsePositives: [
      "An access point steered a client to another band or access point with several frames in a row.",
      "An access point restarted or changed its configuration and told its clients several times."
    ]
  },

  SIGNAL_MISMATCH: {
    name: "Signal Strength Mismatch",
    helpLink: "https://go.nzyme.org/disco-anomalies-signal-mismatch",
    summary: (a) => count(a.frame_count, "frame", "frames") + " " + signalDifference(a) + " than the access point",
    observed: (a) => `The tap recorded ${discoFrames(a, a.frame_count)} sent ${direction(a)} at `
        + `${dbm(a.signal_strength)}. The beacons of this access point arrived at the same tap on the same channel `
        + `at ${dbm(a.reference_signal_strength)}. The frames were ${signalDifference(a)}.`,
    meaning: "Everything an access point sends comes from the same place, so its frames arrive at a tap about as "
        + "strong as the beacons it sent just before. Frames that are much stronger or weaker were sent from "
        + "somewhere else with the address of the access point.",
    falsePositives: [
      "The access point sends beacons with less power or other antennas than its other frames. The difference "
          + "is then about the same every time and usually below 10 dB.",
      "Something between the access point and the tap moved, or the access point changed its transmit power.",
      "A second access point uses the same BSSID."
    ]
  },

  AUTHENTICATION_FLOOD: {
    name: "Authentication Flood",
    helpLink: "https://go.nzyme.org/disco-anomalies-authentication-flood",
    summary: (a) => `Up to ${count(a.peak, "station", "stations")} authenticating within a minute`,
    observed: (a) => `The tap recorded authentication frames from up to ${count(a.peak, "address", "addresses")} per `
        + `minute to the access point, ${count(a.frame_count, "frame", "frames")} in total.`,
    meaning: "An access point keeps state for every station that authenticates. A flood of requests from made "
        + "up addresses fills its client table, and real clients are turned away or disconnected.",
    falsePositives: [
      "Several hundred real clients reconnected to one access point at once."
    ]
  },

  EAPOL_FLOOD: {
    name: "EAPOL Flood",
    helpLink: "https://go.nzyme.org/disco-anomalies-eapol-flood",
    summary: (a) => `Up to ${numeral(a.peak).format("0,0")} ${eapolFrames(a)} within a minute`,
    observed: (a) => `The tap recorded up to ${numeral(a.peak).format("0,0")} ${eapolFrames(a)} per minute for the `
        + `access point, ${count(a.frame_count, "frame", "frames")} in total.`,
    meaning: "A start frame begins a new 802.1X authentication and a logoff frame ends a session. A client "
        + "sends each of them once. A flood of start frames keeps the access point busy with authentications, "
        + "and forged logoff frames end the sessions of connected clients.",
    falsePositives: [
      "Many clients of an 802.1X network connected at once."
    ]
  },

  EAP_FAILURE: {
    name: "Unexpected EAP Failure",
    helpLink: "https://go.nzyme.org/disco-anomalies-eap-failure",
    summary: (a) => a.threshold === 0
        ? count(a.frame_count, "EAP failure", "EAP failures") + " on a network without EAP"
        : `Up to ${count(a.peak, "EAP failure", "EAP failures")} within a minute`,
    observed: (a) => a.threshold === 0
        ? `The tap recorded ${count(a.frame_count, "EAP failure message", "EAP failure messages")} in the name of the `
            + `access point${client(a) ? " to the client" : ""}. The network of this access point does not use EAP.`
        : `The tap recorded up to ${count(a.peak, "EAP failure message", "EAP failure messages")} per minute in the `
            + `name of the access point${client(a) ? " to the client" : ""}.`,
    meaning: "An EAP failure message tells a client that its authentication was rejected, and the client gives "
        + "up the connection attempt. A forged one in the name of the access point keeps a client from connecting.",
    falsePositives: [
      "A client used wrong credentials on an 802.1X network.",
      "A WPS exchange, which always ends with a failure message, on an access point that does not advertise WPS."
    ]
  },

  FRAME_OVERFLOW: {
    name: "Too Many Different Frames",
    helpLink: "https://go.nzyme.org/disco-anomalies-too-many-frames",
    summary: (a) => count(a.frame_count, "frame", "frames") + " the tap could not keep apart",
    observed: (a) => `The tap recorded ${count(a.frame_count, "frame", "frames")} for the access point that it could `
        + `only count, up to ${numeral(a.peak).format("0,0")} within a minute. They were of the `
        + `${a.frame_types.length === 1 ? "type" : "types"} ${a.frame_types.map(connectionFrameTypeToName).join(", ")} `
        + `and came with more different addresses and details than the tap keeps per access point.`,
    meaning: "A tap keeps a limited number of different frames per access point and report. An access point and "
        + "its clients stay far below that. Frames with made up addresses do not, and they can be sent to hide "
        + "other frames among them. The addresses of these frames are not recorded.",
    falsePositives: [
      "A very large number of clients connected to or disconnected from one access point within seconds."
    ]
  }

}

export function discoAnomalyTypeToName(type, title = null) {
  return DISCO_ANOMALY_TYPES[type] ? DISCO_ANOMALY_TYPES[type].name : (title ? title : type);
}

export function discoAnomalySummary(anomaly) {
  const type = DISCO_ANOMALY_TYPES[anomaly.anomaly_type];
  return type ? type.summary(anomaly) : "";
}

export function connectionFrameTypeToName(type) {
  switch (type) {
    case "AUTHENTICATION":
      return "Authentication";
    case "ASSOCIATION_REQUEST":
      return "Association Request";
    case "ASSOCIATION_RESPONSE":
      return "Association Response";
    case "REASSOCIATION_REQUEST":
      return "Reassociation Request";
    case "REASSOCIATION_RESPONSE":
      return "Reassociation Response";
    case "EAPOL_START":
      return "EAPOL Start";
    case "EAPOL_LOGOFF":
      return "EAPOL Logoff";
    case "EAP_REQUEST":
      return "EAP Request";
    case "EAP_RESPONSE":
      return "EAP Response";
    case "EAP_SUCCESS":
      return "EAP Success";
    case "EAP_FAILURE":
      return "EAP Failure";
    case "HANDSHAKE_1":
      return "Handshake Message 1";
    case "HANDSHAKE_2":
      return "Handshake Message 2";
    case "HANDSHAKE_3":
      return "Handshake Message 3";
    case "HANDSHAKE_4":
      return "Handshake Message 4";
    case "DEAUTHENTICATION":
      return "Deauthentication";
    case "DISASSOCIATION":
      return "Disassociation";
    default:
      return type;
  }
}
