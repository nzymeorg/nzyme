use strum_macros::{EnumIter, Display};

#[derive(Debug, Clone, EnumIter, Display)]
pub enum WiredChannelName {
    EthernetBroker,
    ArpPipeline,
    IcmpPipeline,
    NdpPipeline,
    TcpPipeline,
    UdpPipeline,
    DnsPipeline,
    SocksPipeline,
    SshPipeline,
    TlsPipeline,
    RtspPipeline,
    StunPipeline,
    Dhcpv4Pipeline,
    Dhcpv6Pipeline,
    NtpPipeline,
    WebRtcPipeline,
    VpnPipeline,
    Ipv6TransitionPipeline
}

#[derive(Debug, Clone, EnumIter, Display)]
pub enum Dot11ChannelName {
    Dot11Broker,
    Dot11FramesPipeline,
}

#[derive(Debug, Clone, EnumIter, Display)]
pub enum BluetoothChannelName {
    BluetoothDevicesPipeline,
}

#[derive(Debug, Clone, EnumIter, Display)]
pub enum GenericChannelName {
    UavRemoteIdPipeline
}
