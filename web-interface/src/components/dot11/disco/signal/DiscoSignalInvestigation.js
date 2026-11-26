import ApiRoutes from "../../../../util/ApiRoutes";
import {DISCO_MENU_ITEMS} from "../DiscoMenuItems";
import {DISCO_FILTER_FIELDS} from "../DiscoFilterFields";
import {discoTypeToName} from "../DiscoType";
import DiscoSignalFrameRecords from "./DiscoSignalFrameRecords";
import {discoFramesLink, discoSignalLink, discoSignalStartLink} from "./DiscoSignalLinks";

export const DISCO_SIGNAL_INVESTIGATION = {
  apiPath: "/dot11/disco/signal",
  pageTitle: "WiFi Disconnection Signal Strength Investigation",
  menuItems: DISCO_MENU_ITEMS,
  route: ApiRoutes.DOT11.DISCO.SIGNAL,
  filterFields: DISCO_FILTER_FIELDS,
  addressField: "transmitter",
  frames: "disconnection frames",
  framesLabel: "Disconnection frames",
  framesTitle: "Disconnection Frames",
  transmitters: "transmitters",
  transmitterTitle: "Transmitter",
  transmittersTitle: "Transmitters of Disconnection Frames",
  hasReceivers: true,
  addressLabel: "Address of the access point or client to investigate",
  introduction: "This page compares the signal strength of disconnection frames sent in the name of an address with "
      + "the other frames that address sent around the same time. Frames that are much stronger or weaker were likely "
      + "sent by another device.",
  recordsTitle: "Frame Records",
  recordsHelpLink: "https://go.nzyme.org/disco-frames",
  Records: DiscoSignalFrameRecords,
  link: discoSignalLink,
  startLink: discoSignalStartLink,
  recordsLink: discoFramesLink,
  describeFrame: (frame) => [
    frame.transmitter + " to " + (frame.is_broadcast ? "all clients" : frame.receiver),
    discoTypeToName(frame.frame_type)
        + (frame.reason_code === null || frame.reason_code === undefined ? "" : ", reason code " + frame.reason_code)
  ]
}
