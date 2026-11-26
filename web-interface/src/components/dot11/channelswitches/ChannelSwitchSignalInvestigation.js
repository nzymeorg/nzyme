import React from "react";
import ApiRoutes from "../../../util/ApiRoutes";
import {CHANNEL_SWITCH_MENU_ITEMS} from "./ChannelSwitchMenuItems";
import {CHANNEL_SWITCH_FILTER_FIELDS} from "./ChannelSwitchFilterFields";
import {channelSwitchFrameTypeToName} from "./ChannelSwitchNames";
import ChannelSwitchAnnouncements from "./ChannelSwitchAnnouncements";
import {
  channelSwitchAnnouncementsLink,
  channelSwitchSignalLink,
  channelSwitchSignalStartLink
} from "./ChannelSwitchSignalLinks";

export const CHANNEL_SWITCH_SIGNAL_INVESTIGATION = {
  apiPath: "/dot11/channelswitches/signal",
  pageTitle: "WiFi Channel Switch Signal Strength Investigation",
  menuItems: CHANNEL_SWITCH_MENU_ITEMS,
  route: ApiRoutes.DOT11.CHANNEL_SWITCHES.SIGNAL,
  filterFields: CHANNEL_SWITCH_FILTER_FIELDS,
  addressField: "bssid",
  frames: "announcing frames",
  framesLabel: "Announcing frames",
  framesTitle: "Announcing Frames",
  transmitters: "access points",
  transmitterTitle: "Access Point",
  transmittersTitle: "Access Points With Channel Switch Announcements",
  hasReceivers: false,
  addressLabel: "BSSID of the access point to investigate",
  introduction: "This page compares the signal strength of frames that announced a channel switch in the name of an "
      + "access point with the beacons of that access point that carried no announcement around the same time. Frames "
      + "that are much stronger or weaker were likely sent by another device.",
  recordsTitle: "Announcements",
  recordsHelpLink: "https://go.nzyme.org/channel-switches-recording",
  Records: ({timeRange, filters, setFilters, revision}) => {
    return React.createElement(ChannelSwitchAnnouncements,
        {timeRange: timeRange, filters: filters, setFilters: setFilters, revision: revision});
  },
  link: channelSwitchSignalLink,
  startLink: channelSwitchSignalStartLink,
  recordsLink: channelSwitchAnnouncementsLink,
  describeFrame: (frame) => [
    frame.transmitter,
    channelSwitchFrameTypeToName(frame.frame_type) + " that announced a switch to channel " + frame.new_channel
  ]
}
