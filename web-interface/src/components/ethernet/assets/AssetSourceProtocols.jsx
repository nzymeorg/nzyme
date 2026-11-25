export default function AssetSourceProtocols(props) {

  const asset = props.asset;

  const protocols = [];

  if (asset.seen_arp) {
    protocols.push("ARP");
  }

  if (asset.seen_ndp) {
    protocols.push("NDP");
  }

  if (asset.seen_dhcp) {
    protocols.push("DHCP");
  }

  if (asset.seen_tcp) {
    protocols.push("TCP");
  }

  if (asset.seen_udp) {
    protocols.push("UDP");
  }

  return protocols.join(", ")

}