// The roles of a MAC address that comes without its asset.
export const MAC_ROLE_NOTES = {
  UNCLAIMED: {
    icon: "fa-circle-question",
    note: "This MAC address did not claim the IP address by ARP or NDP. It usually belongs to a router that forwarded the traffic."
  },
  PUBLIC: {
    icon: "fa-globe",
    note: "The IP address is public. This MAC address usually belongs to the gateway."
  }
};

// Where the role is not known, the site-local attribute decides.
export const macIsDevice = (address) => {
  const role = address.mac ? address.mac.role : null;

  return role === "DEVICE"
      || (!MAC_ROLE_NOTES[role] && Boolean(address.attributes && address.attributes.is_site_local));
}
