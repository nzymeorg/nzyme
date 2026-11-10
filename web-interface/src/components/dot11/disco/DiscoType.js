export function discoTypeToName(type) {
  switch (type) {
    case "DEAUTHENTICATION":
      return "Deauthentication";
    case "DISASSOCIATION":
      return "Disassociation";
    default:
      return type;
  }
}
