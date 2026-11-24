import ApiRoutes from "../../util/ApiRoutes";

const KEY = "search_return";

/*
 * The search results a user came from, so the navigation bar can offer the way back from any page. Kept in session
 * storage: every tab has its own search to return to, and nothing is left when the tab closes. It does not expire:
 * the user dismisses it, signs out or searches for something else.
 */
const read = () => {
  try {
    return JSON.parse(window.sessionStorage.getItem(KEY));
  } catch {
    return null;
  }
}

const write = (stored) => {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(stored));
  } catch {
    // Without storage there is no way back to offer.
  }
}

const idOf = (user) => user && user.id ? user.id : null;

const ownedBy = (stored, user) => !!stored
  && typeof stored.query === "string"
  && typeof stored.path === "string"
  && stored.path.startsWith(ApiRoutes.SEARCH.RESULTS + "?")
  && stored.user === idOf(user);

export function rememberSearchReturn(user, path, query) {
  write({user: idOf(user), path: path, query: query, group: null});
}

export function searchReturn(user) {
  const stored = read();

  return ownedBy(stored, user) ? stored : null;
}

export function updateSearchReturn(user, changes) {
  const stored = read();

  if (ownedBy(stored, user)) {
    write({...stored, ...changes});
  }
}

export function clearSearchReturn() {
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    // Without storage nothing was kept.
  }
}
