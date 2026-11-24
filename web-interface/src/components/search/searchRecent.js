import Store from "../../util/Store";
import {MAXIMUM_QUERY_LENGTH, MINIMUM_QUERY_LENGTH} from "./searchQuery";

const MAXIMUM = 6;

// Kept per user: a browser can be shared, and what one user looked for is not for the next one to see.
const keyOf = (user) => "search_recent_" + (user && user.id ? user.id : "unknown");

export function recentSearches(user) {
  const stored = Store.get(keyOf(user));

  return Array.isArray(stored)
    ? stored.filter((query) => typeof query === "string" && query.trim().length >= MINIMUM_QUERY_LENGTH
        && query.length <= MAXIMUM_QUERY_LENGTH)
        .slice(0, MAXIMUM)
    : [];
}

export function rememberSearch(user, query) {
  const trimmed = query.trim();
  if (trimmed.length < MINIMUM_QUERY_LENGTH || trimmed.length > MAXIMUM_QUERY_LENGTH) {
    return;
  }

  const others = recentSearches(user).filter((recent) => recent.toLowerCase() !== trimmed.toLowerCase());
  Store.set(keyOf(user), [trimmed].concat(others).slice(0, MAXIMUM));
}

export function clearRecentSearches(user) {
  Store.delete(keyOf(user));
}
