import ApiRoutes from "../../util/ApiRoutes";

export const MINIMUM_QUERY_LENGTH = 2;
export const MAXIMUM_QUERY_LENGTH = 128;

// The dropdown and the results page ask for the same number of hits, so the page shows what the dropdown loaded.
export const HITS_PER_GROUP = 10;

export const TIME_RANGE_KEY = "search";
export const TIME_RANGE_PARAMETER = "tr_" + TIME_RANGE_KEY;

export const normalizedQuery = (query) => query ? query.trim().replace(/\s+/g, " ") : "";

export const queryOfLocation = (location) => location.pathname === ApiRoutes.SEARCH.RESULTS
  ? (new URLSearchParams(location.search).get("q") || "").trim() : "";
