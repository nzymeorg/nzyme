use strum_macros::Display;

#[derive(Eq, PartialEq, Debug, Clone, Display)]
pub enum ContextSource {
    PtrDns,
    Dhcp,
    Arp,
    Ndp,
    Tcp,
    Udp
}