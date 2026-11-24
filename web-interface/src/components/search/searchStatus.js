export function failureOf(group) {
  switch (group.status) {
    case "TIMEOUT": return group.title + " took too long to search.";
    case "BUSY": return group.title + " was not searched because too many searches are running. Try again in a moment.";
    case "ERROR": return group.title + " could not be searched.";
    default: return null;
  }
}
