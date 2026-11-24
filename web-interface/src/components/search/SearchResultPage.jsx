import React, {useContext, useEffect, useMemo, useRef, useState} from "react";
import {Link, useLocation, useNavigate, useNavigationType} from "react-router-dom";
import moment from "moment";
import ApiRoutes from "../../util/ApiRoutes";
import usePageTitle from "../../util/UsePageTitle";
import {UserContext} from "../../App";
import useSearch, {orderedGroups, serializedRange} from "./useSearch";
import {
  HITS_PER_GROUP, MAXIMUM_QUERY_LENGTH, MINIMUM_QUERY_LENGTH, TIME_RANGE_KEY, TIME_RANGE_PARAMETER, normalizedQuery,
  queryOfLocation
} from "./searchQuery";
import {findPages, pagesOf} from "./searchPages";
import {rangeOfGroup, resolveSearchLink} from "./searchLinks";
import {hitIcon, interpretationTitle} from "./searchTypes";
import {failureOf} from "./searchStatus";
import {rememberSearch} from "./searchRecent";
import {rememberSearchReturn, searchReturn, updateSearchReturn} from "./searchReturn";
import SearchHighlight, {needlesOf} from "./SearchHighlight";
import {useUrlTimeRange} from "../shared/timerange/TimeRangeSelector";
import {Presets} from "../shared/timerange/TimeRange";
import CardTitleWithControls from "../shared/CardTitleWithControls";

function SearchResultPage() {

  const user = useContext(UserContext);
  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();

  const query = queryOfLocation(location);
  const rangeParameter = new URLSearchParams(location.search).get(TIME_RANGE_PARAMETER);

  usePageTitle(query ? "Search: " + query : "Search");

  const [value, setValue] = useState(query);
  const [urlTimeRange, setTimeRange] = useUrlTimeRange(null, TIME_RANGE_KEY);
  const timeRange = serializedRange(urlTimeRange) ? urlTimeRange : null;
  const [attempt, setAttempt] = useState(0);

  const search = useSearch(query, {limit: HITS_PER_GROUP, timeRange: timeRange, attempt: attempt});

  const pages = useMemo(() => findPages(pagesOf(user), query), [user, query]);

  // The group of the result that was opened from here, when the user comes back with the browser or the link.
  const [returnTo] = useState(() => {
    const stored = searchReturn(user);
    const returning = navigationType === "POP" || (location.state && location.state.returned);

    return returning && stored && stored.group && stored.query === query ? stored.group : null;
  });
  const returned = useRef(false);

  useEffect(() => {
    setValue(query);
    rememberSearch(user, query);
  }, [query]);

  useEffect(() => {
    if (query.length < MINIMUM_QUERY_LENGTH) {
      return;
    }

    const params = new URLSearchParams({q: query});
    if (rangeParameter) {
      params.set(TIME_RANGE_PARAMETER, rangeParameter);
    }

    rememberSearchReturn(user, ApiRoutes.SEARCH.RESULTS + "?" + params.toString(), query);
  }, [query, rangeParameter]);

  // While the next query loads, the hook still holds the results of the one before. A page of results shows none.
  const current = search.status === "done" && search.query.toLowerCase() === normalizedQuery(query).toLowerCase();
  const searching = query.length >= MINIMUM_QUERY_LENGTH;
  const groups = current ? orderedGroups(search.groups) : [];
  const interpretations = current ? search.interpretations : [];
  const found = groups.filter((group) => group.hits.length > 0);
  const failures = groups.map(failureOf).filter((failure) => failure !== null);
  const needles = needlesOf(query, interpretations);
  const loading = searching && !current && search.status !== "failed";
  const complete = current && search.pending.length === 0;
  const nothing = complete && found.length === 0 && pages.length === 0;

  // Groups of recorded data arrive one by one, so the group to return to can show up late or not at all.
  useEffect(() => {
    if (!returnTo || returned.current) {
      return;
    }

    const target = document.getElementById("search-" + returnTo);
    if (target) {
      target.scrollIntoView({block: "start"});
    }

    if (target || complete) {
      returned.current = true;
    }
  }, [found.length, complete]);

  const submit = (e) => {
    e.preventDefault();

    if (value.trim().length < MINIMUM_QUERY_LENGTH) {
      return;
    }

    // The same query again is a retry, for a search that failed or a source that was busy.
    if (value.trim() === query) {
      setAttempt(attempt + 1);
      return;
    }

    const params = new URLSearchParams(location.search);
    params.set("q", value.trim());
    navigate({search: params.toString()});
  }

  const resetTimeRange = (e) => {
    e.preventDefault();

    const params = new URLSearchParams(location.search);
    params.delete(TIME_RANGE_PARAMETER);
    navigate({search: params.toString()});
  }

  const sections = [];
  if (pages.length > 0) {
    sections.push({id: "search-pages", title: "Pages", count: pages.length, more: false});
  }
  found.forEach((group) => {
    sections.push({id: "search-" + group.source, title: group.title, count: group.hits.length, more: group.has_more || !!group.limited});
  });

  const summary = () => {
    if (!searching) {
      return "Enter at least " + MINIMUM_QUERY_LENGTH + " characters to search.";
    }

    if (search.status === "failed") {
      return "The search failed.";
    }

    if (loading) {
      return "Searching.";
    }

    if (current && search.pending.length > 0) {
      return "Searching recorded network data: " + search.pending.map((source) => source.title).join(", ") + ".";
    }

    return nothing ? "Nothing found." : null;
  }

  const hitRow = (group, hit, i) => {
    const href = resolveSearchLink(hit.link, rangeOfGroup(group, timeRange));

    return (
      <tr key={i}>
        <td className="search-result-icon"><i className={hitIcon(hit.type)} /></td>
        <td className="search-result-title">
          {href
            ? <Link to={href} onClick={() => updateSearchReturn(user, {group: group.source})}>
                <SearchHighlight text={hit.title} needles={needles} />
              </Link>
            : <SearchHighlight text={hit.title} needles={needles} />}
          {hit.subtitle
            ? <div className="search-result-subtitle"><SearchHighlight text={hit.subtitle} needles={needles} /></div>
            : null}
        </td>
        <td className="search-result-facts">
          {(hit.facts || []).map((fact, f) => (
            <div key={f}>
              <span className="search-fact-label">{fact.label}</span>{" "}
              <SearchHighlight text={fact.value} needles={needles} />
            </div>
          ))}
        </td>
        <td className="search-result-time">
          {hit.timestamp
            ? <span title={moment(hit.timestamp).format()}>{moment(hit.timestamp).fromNow()}</span> : null}
        </td>
      </tr>
    );
  }

  const terms = interpretations.filter((term) => term.detail || resolveSearchLink(term.link));

  const termCard = (term, i) => {
    const href = resolveSearchLink(term.link);

    return (
      <div className="row mt-3" key={"term-" + i}>
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title={interpretationTitle(term.kind)} />

              <table className="table table-sm table-hover table-striped search-results mb-0">
                <thead>
                <tr>
                  <th className="search-result-icon"></th>
                  <th>Result</th>
                  <th>Details</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                  <td className="search-result-icon"><i className={hitIcon(term.kind)} /></td>
                  <td className="search-result-title">{href ? <Link to={href}>{term.value}</Link> : term.value}</td>
                  <td className="search-result-facts">{term.detail}</td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const groupCard = (group) => {
    const range = rangeOfGroup(group, timeRange);

    return (
      <div className="row mt-3 search-result-group" key={group.source} id={"search-" + group.source}>
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title={group.title}
                                     internalLink={resolveSearchLink(group.all_link, range)}
                                     fixedAppliedTimeRange={group.tier === "RECORDED" ? range : null} />

              <table className="table table-sm table-hover table-striped search-results mb-0">
                <thead>
                <tr>
                  <th className="search-result-icon"></th>
                  <th>Result</th>
                  <th>Details</th>
                  <th>Last Seen</th>
                </tr>
                </thead>
                <tbody>
                {group.hits.map((hit, i) => hitRow(group, hit, i))}
                </tbody>
              </table>

              {group.has_more ? (
                <button className="btn btn-sm btn-outline-secondary mt-2" disabled={!!group.loading_more}
                        onClick={() => search.loadMore(group.source)}>
                  {group.loading_more ? "Loading" : "Show More"}
                </button>
              ) : null}

              {group.more_failed
                ? <div className="search-result-subtitle mt-2">More results could not be loaded.</div> : null}

              {group.limited ? (
                <div className="search-result-subtitle mt-2">
                  Limited to the first {group.hits.length.toLocaleString()} results. More results exist.
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-12">
          <h1>Search</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Query"
                                     timeRange={timeRange ? timeRange : Presets.RELATIVE_HOURS_24}
                                     setTimeRange={setTimeRange}
                                     urlKey={TIME_RANGE_KEY}
                                     helpLink="https://go.nzyme.org/search" />

              <form role="search" onSubmit={submit}>
                <div className="input-group">
                  <input className="form-control"
                         type="text"
                         aria-label="Search"
                         autoComplete="off"
                         spellCheck={false}
                         maxLength={MAXIMUM_QUERY_LENGTH}
                         value={value}
                         onChange={(e) => setValue(e.target.value)} />
                  <button className="btn btn-primary" type="submit">Search</button>
                </div>
              </form>

              <p className="search-scope mb-0 mt-3">
                All taps are searched and the time range applies to recorded network data.{" "}
                {timeRange ? <a href="#" onClick={resetTimeRange}>Reset Time Range</a> : null}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Results" helpLink="https://go.nzyme.org/search-groups" />

              {summary() ? (
                <p className="search-summary mb-0">
                  {complete || !searching || search.status === "failed"
                    ? null : <i className="fa-solid fa-circle-notch fa-spin" />} {summary()}
                </p>
              ) : null}

              {failures.map((failure, i) => (
                <div className="alert alert-warning mt-3 mb-0" key={i}>{failure}</div>
              ))}

              {sections.length > 0 ? (
                <ul className={"search-sections" + (summary() || failures.length > 0 ? " mt-3" : "")}>
                  {sections.map((section) => (
                    <li key={section.id}>
                      <a href={"#" + section.id} onClick={(e) => {
                        e.preventDefault();
                        const target = document.getElementById(section.id);
                        if (target) {
                          target.scrollIntoView({behavior: "smooth", block: "start"});
                        }
                      }}>{section.title}</a>{" "}
                      <span className="search-section-count">{section.count}{section.more ? "+" : ""}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          </div>
        </div>
      </div>

      {terms.map(termCard)}

      {pages.length > 0 ? (
        <div className="row mt-3 search-result-group" id="search-pages">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Pages" />

                <table className="table table-sm table-hover table-striped search-results mb-0">
                  <tbody>
                  {pages.map((page, i) => (
                    <tr key={i}>
                      <td className="search-result-icon"><i className={hitIcon("PAGE")} /></td>
                      <td className="search-result-title">
                        <Link to={page.href} onClick={() => updateSearchReturn(user, {group: "pages"})}>
                          <SearchHighlight text={page.title} needles={needles} />
                        </Link>
                      </td>
                      <td className="search-result-facts">{page.path.join(" / ")}</td>
                    </tr>
                  ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {found.map(groupCard)}
    </React.Fragment>
  )

}

export default SearchResultPage;
