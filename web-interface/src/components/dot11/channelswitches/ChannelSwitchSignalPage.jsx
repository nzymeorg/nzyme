import React from "react";
import FrameSignalPage from "../signal/FrameSignalPage";
import {CHANNEL_SWITCH_SIGNAL_INVESTIGATION} from "./ChannelSwitchSignalInvestigation";

export default function ChannelSwitchSignalPage() {

  return <FrameSignalPage investigation={CHANNEL_SWITCH_SIGNAL_INVESTIGATION} />

}
