export default function TransparentContextSource(props) {

  const source = props.source;

  switch (source) {
    case "Arp":
      return "ARP"
    case "Ndp":
      return "NDP"
    case "PtrDns":
      return "Reverse DNS"
    case "Dhcp":
      return "DHCP"
    case "Tcp":
      return "TCP"
    case "Udp":
      return "UDP"
  }

}