import React, {useContext, useEffect, useMemo, useRef, useState} from "react";
import {Link, useLocation, useNavigate} from "react-router-dom";
import moment from "moment";
import ApiRoutes from "../../util/ApiRoutes";
import {UserContext} from "../../App";
import useSearch, {orderedGroups, serializedRange} from "./useSearch";
import {
  HITS_PER_GROUP, MAXIMUM_QUERY_LENGTH, MINIMUM_QUERY_LENGTH, TIME_RANGE_KEY, TIME_RANGE_PARAMETER, normalizedQuery,
  queryOfLocation
} from "./searchQuery";
import {findPages, pagesOf} from "./searchPages";
import {rangeOfGroup, resolveSearchLink} from "./searchLinks";
import {hitIcon, interpretationLabel} from "./searchTypes";
import {failureOf} from "./searchStatus";
import {clearRecentSearches, recentSearches, rememberSearch} from "./searchRecent";
import {clearSearchReturn, searchReturn} from "./searchReturn";
import SearchHighlight, {needlesOf} from "./SearchHighlight";
import {timeRangeFromURLOrDefault} from "../shared/timerange/TimeRangeSelector";
import TimeRangeDescription from "../shared/timerange/TimeRangeDescription";

const HITS_SHOWN = 3;
const PAGES_SHOWN = 4;
const TYPING_DELAY_MS = 150;
const RECORDED_DELAY_MS = 350;
const RESULTS_ID = "nav-search-results";

// A dialog or a full screen view lies over the navigation bar. The field then takes no shortcut.
const covered = (element) => {
  const box = element.getBoundingClientRect();
  const top = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);

  return !top || !element.parentNode.contains(top);
}

const typingElsewhere = (target) => target
  && (["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName) || target.isContentEditable);

export default function NavigationSearch() {

  const user = useContext(UserContext);
  const navigate = useNavigate();
  const location = useLocation();

  const [value, setValue] = useState(() => queryOfLocation(location));
  const [open, setOpen] = useState(false);
  const [recent, setRecent] = useState(() => recentSearches(user));

  /*
   * The row chosen with the arrow keys, by its key. Only the keyboard chooses a row: a mouse that happens to rest
   * over the results must not decide what Enter opens. Kept by key and not by position, because groups of
   * recorded data arrive later and move the rows below them.
   */
  const [active, setActive] = useState(null);

  const root = useRef(null);
  const input = useRef(null);
  const scroller = useRef(null);

  const query = value.trim();
  const searching = query.length >= MINIMUM_QUERY_LENGTH;

  // On the results page the dropdown searches the time range picked there, as Enter does.
  const onResultsPage = location.pathname === ApiRoutes.SEARCH.RESULTS;
  const pageRange = onResultsPage ? timeRangeFromURLOrDefault(null, TIME_RANGE_KEY) : null;
  const timeRange = serializedRange(pageRange) ? pageRange : null;

  const search = useSearch(value, {
    limit: HITS_PER_GROUP, timeRange: timeRange, delay: TYPING_DELAY_MS, recordedDelay: RECORDED_DELAY_MS,
    enabled: open
  });

  const pages = useMemo(() => pagesOf(user), [user]);
  const matchingPages = useMemo(() => findPages(pages, query), [pages, query]);

  // Results of the query before stay up while the next one loads, so the list does not flicker while typing.
  const groups = orderedGroups(search.groups);
  const found = groups.filter((group) => group.hits.length > 0);
  const failures = groups.map(failureOf).filter((failure) => failure !== null);
  const needles = needlesOf(query, search.interpretations);

  const shownQuery = queryOfLocation(location);

  const resultsPath = (text) => {
    const params = new URLSearchParams();
    params.set("q", text.trim());

    const range = onResultsPage ? new URLSearchParams(location.search).get(TIME_RANGE_PARAMETER) : null;
    if (range) {
      params.set(TIME_RANGE_PARAMETER, range);
    }

    return ApiRoutes.SEARCH.RESULTS + "?" + params.toString();
  }

  // Pages keep filters and time ranges in the URL and change it on their own. That must not touch the field.
  useEffect(() => {
    setOpen(false);
    setValue(shownQuery);
  }, [location.pathname, shownQuery]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (typeof e.key !== "string" || e.altKey) {
        return;
      }

      // Some keyboard layouts have the slash on a shifted key.
      const slash = e.key === "/" && !e.ctrlKey && !e.metaKey;
      const palette = e.key.toLowerCase() === "k" && (e.ctrlKey || e.metaKey) && !e.shiftKey;

      if ((slash || palette) && !typingElsewhere(e.target) && input.current && !covered(input.current)) {
        e.preventDefault();
        input.current.focus();
        input.current.select();
      }
    }

    const onMouseDown = (e) => {
      if (root.current && !root.current.contains(e.target)) {
        setOpen(false);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    document.addEventListener("mousedown", onMouseDown);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("mousedown", onMouseDown);
    }
  }, []);

  useEffect(() => {
    setActive(null);
  }, [query, open]);

  useEffect(() => {
    if (active === null || !scroller.current) {
      return;
    }

    const row = scroller.current.querySelector("[data-row=\"" + active + "\"]");
    if (row) {
      row.scrollIntoView({block: "nearest"});
    }
  }, [active]);

  // Everything the arrow keys can choose, in the order it is shown. Each row adds itself while rendering.
  const rows = [];

  const leave = () => {
    if (searching) {
      rememberSearch(user, query);

      // A result opened for another query is a new search. The results of the one before are no place to return to.
      const stored = searchReturn(user);
      if (stored && stored.query.toLowerCase() !== query.toLowerCase()) {
        clearSearchReturn();
      }
    }

    setOpen(false);
    if (input.current) {
      input.current.blur();
    }
  }

  const submit = (e) => {
    e.preventDefault();

    const chosen = active === null ? null : rows.find((row) => row.key === active);
    if (chosen && chosen.onChoose) {
      chosen.onChoose();
      return;
    }

    if (chosen) {
      leave();
      navigate(chosen.href);
      return;
    }

    if (searching) {
      leave();
      navigate(resultsPath(query));
    }
  }

  const onKeyDown = (e) => {
    if (e.nativeEvent && e.nativeEvent.isComposing) {
      return;
    }

    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();

      if (!open) {
        setOpen(true);
        return;
      }

      // Past the last row and before the first one is no row: Enter then opens all results again.
      const index = rows.findIndex((row) => row.key === active);
      const next = e.key === "ArrowDown"
        ? index + 1
        : (index === -1 ? rows.length - 1 : index - 1);

      setActive(next >= 0 && next < rows.length ? rows[next].key : null);
    } else if (e.key === "Escape") {
      if (open) {
        // Pages close their own dialogs on Escape. This one only closes the results.
        e.stopPropagation();
        setOpen(false);
      }
      e.target.blur();
    } else if (e.key === "Tab") {
      setOpen(false);
    }
  }

  const linkRow = (key, href, onChoose, children) => {
    rows.push({key: key, href: href, onChoose: onChoose});

    return (
      <Link key={key} to={href}
            id={RESULTS_ID + "-" + key}
            data-row={key}
            role="option"
            aria-selected={key === active}
            className={"search-row" + (key === active ? " search-row-active" : "")}
            onClick={(e) => {
              if (onChoose) {
                e.preventDefault();
                onChoose();
                return;
              }

              leave();
            }}>
        {children}
      </Link>
    );
  }

  const hitRow = (group, hit, i) => {
    const href = resolveSearchLink(hit.link, rangeOfGroup(group, timeRange));
    const key = group.source + "-" + i;

    const body = (
      <React.Fragment>
        <i className={"search-row-icon " + hitIcon(hit.type)} />
        <span className="search-row-body">
          <span className="search-row-title"><SearchHighlight text={hit.title} needles={needles} /></span>
          <span className="search-row-detail">
            {hit.subtitle ? <span><SearchHighlight text={hit.subtitle} needles={needles} /></span> : null}
            {(hit.facts || []).slice(0, 2).map((fact, f) => (
              <span key={f} className={f === 0 ? "search-row-fact-first" : null}>
                <span className="search-fact-label">{fact.label}</span>{" "}
                <SearchHighlight text={fact.value} needles={needles} />
              </span>
            ))}
          </span>
        </span>
        {hit.timestamp
          ? <span className="search-row-time" title={moment(hit.timestamp).format()}>{moment(hit.timestamp).fromNow()}</span>
          : null}
      </React.Fragment>
    );

    return href ? linkRow(key, href, null, body) : <div key={key} className="search-row search-row-static">{body}</div>;
  }

  const groupNote = (shown, more, range) => {
    if (!more && !range) {
      return null;
    }

    return (
      <span className="search-group-coverage">
        {more ? "First " + shown : null}{more && range ? ", " : null}
        {range ? <TimeRangeDescription timeRange={range} /> : null}
      </span>
    );
  }

  const results = () => {
    const interpretations = search.query.toLowerCase() === normalizedQuery(value).toLowerCase()
      ? search.interpretations : [];
    const nothing = search.status === "done" && search.pending.length === 0 && found.length === 0
      && matchingPages.length === 0 && failures.length === 0 && interpretations.every((i) => !i.link);

    return (
      <React.Fragment>
        {interpretations.map((interpretation, i) => {
          const href = resolveSearchLink(interpretation.link);
          const body = (
            <React.Fragment>
              <i className={"search-row-icon " + (href ? "fa-solid fa-arrow-right" : "fa-solid fa-circle-info")} />
              <span className="search-row-body">
                <span className="search-row-title">
                  {interpretationLabel(interpretation.kind)} <span className="search-value">{interpretation.value}</span>
                </span>
                {interpretation.detail ? <span className="search-row-detail"><span>{interpretation.detail}</span></span> : null}
              </span>
              {href ? <span className="search-row-time">Open Details</span> : null}
            </React.Fragment>
          );

          return href
            ? linkRow("interpretation-" + i, href, null, body)
            : <div key={"interpretation-" + i} className="search-row search-row-static">{body}</div>;
        })}

        {matchingPages.length > 0 ? (
          <div className="search-group">
            <div className="search-group-title">
              <span>Pages</span>
              {groupNote(PAGES_SHOWN, matchingPages.length > PAGES_SHOWN, null)}
            </div>
            {matchingPages.slice(0, PAGES_SHOWN).map((page, i) => linkRow("page-" + i, page.href, null, (
              <React.Fragment>
                <i className={"search-row-icon " + hitIcon("PAGE")} />
                <span className="search-row-body">
                  <span className="search-row-title"><SearchHighlight text={page.title} needles={needles} /></span>
                  {page.path.length > 0
                    ? <span className="search-row-detail"><span>{page.path.join(" / ")}</span></span> : null}
                </span>
              </React.Fragment>
            )))}
          </div>
        ) : null}

        {found.map((group) => (
          <div key={group.source} className="search-group">
            <div className="search-group-title">
              <span>{group.title}</span>
              {groupNote(HITS_SHOWN, group.hits.length > HITS_SHOWN || group.has_more,
                group.tier === "RECORDED" ? rangeOfGroup(group, timeRange) : null)}
            </div>
            {group.hits.slice(0, HITS_SHOWN).map((hit, i) => hitRow(group, hit, i))}
          </div>
        ))}

        {search.status === "loading" && found.length === 0 ? (
          <div className="search-note"><i className="fa-solid fa-circle-notch fa-spin" /> Searching.</div>
        ) : null}

        {search.pending.length > 0 ? (
          <div className="search-note">
            <i className="fa-solid fa-circle-notch fa-spin" /> Searching recorded network
            data: {search.pending.map((source) => source.title).join(", ")}.
          </div>
        ) : null}

        {failures.map((failure, i) => <div key={i} className="search-note">{failure}</div>)}

        {search.status === "failed" ? <div className="search-note">The search failed.</div> : null}

        {nothing ? <div className="search-note">Nothing found.</div> : null}
      </React.Fragment>
    );
  }

  const start = () => {
    if (recent.length === 0) {
      return (
        <div className="search-note">
          Searches MAC addresses, IP addresses, networks, names and any other text.
        </div>
      );
    }

    return (
      <div className="search-group">
        <div className="search-group-title">
          <span>Recent Searches</span>
          <a href="#" className="search-group-coverage" onClick={(e) => {
            e.preventDefault();
            clearRecentSearches(user);
            setRecent([]);
          }}>Clear</a>
        </div>
        {recent.map((recentQuery, i) => linkRow("recent-" + i, resultsPath(recentQuery), () => {
          setValue(recentQuery);
          if (input.current) {
            input.current.focus();
          }
        }, (
          <React.Fragment>
            <i className="search-row-icon fa-solid fa-clock-rotate-left" />
            <span className="search-row-body"><span className="search-row-title">{recentQuery}</span></span>
          </React.Fragment>
        )))}
      </div>
    );
  }

  const body = searching ? results() : start();

  return (
    <div className="nav-search" ref={root}>
      <form role="search" onSubmit={submit}>
        <div className="nav-search-field">
          <i className="nav-search-icon fa-solid fa-magnifying-glass" />
          <input ref={input}
                 className="form-control"
                 type="text"
                 role="combobox"
                 placeholder="Search"
                 aria-label="Search"
                 aria-expanded={open}
                 aria-controls={RESULTS_ID}
                 aria-autocomplete="list"
                 aria-activedescendant={active === null ? undefined : RESULTS_ID + "-" + active}
                 autoComplete="off"
                 autoCorrect="off"
                 autoCapitalize="off"
                 spellCheck={false}
                 maxLength={MAXIMUM_QUERY_LENGTH}
                 value={value}
                 onChange={(e) => {
                   setValue(e.target.value);
                   setOpen(true);
                 }}
                 onFocus={() => {
                   setRecent(recentSearches(user));
                   setOpen(true);
                 }}
                 onKeyDown={onKeyDown} />
          {value
            ? (
              <button type="button" className="nav-search-clear" title="Clear" aria-label="Clear"
                      onClick={() => {
                        setValue("");
                        if (input.current) {
                          input.current.focus();
                        }
                      }}>
                <i className="fa-solid fa-xmark" />
              </button>
            )
            : <kbd className="nav-search-shortcut">/</kbd>}
        </div>
      </form>

      {open ? (
        <div className="nav-search-panel">
          <div className="nav-search-results" id={RESULTS_ID} role="listbox" ref={scroller}>
            {body}
          </div>

          {searching ? (
            <Link to={resultsPath(query)} className="nav-search-all" onClick={leave}>
              <span><i className="fa-solid fa-magnifying-glass" /> Show All Results</span>
              {active === null ? <kbd>Enter</kbd> : null}
            </Link>
          ) : null}
        </div>
      ) : null}
    </div>
  );

}
