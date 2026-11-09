function Subsystem(props) {

  const subsystem = props.subsystem;

  switch (subsystem) {
    case "GENERIC":
      return "Generic / Other";
    case "DOT11":
      return "802.11 / WiFi";
    case "ETHERNET":
      return "Ethernet";
    case "BLUETOOTH":
      return "Bluetooth";
    case "UAV":
      return "UAV"
    default:
      return "Unknown";
  }

}

export default Subsystem;