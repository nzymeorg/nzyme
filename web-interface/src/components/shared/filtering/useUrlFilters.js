import {useEffect, useRef, useState} from "react";
import {useLocation} from "react-router-dom";
import {filtersToQueryParameters, queryParametersToFilters} from "./FilterQueryParameters";

/*
 * Page filters are mirrored into the "filters" URL query parameter by the Filters component. With client-side
 * navigation, that parameter can change while the page component stays mounted, for example when a link points at
 * the same page with a different set of filters. This hook reads the parameter on first render like a plain
 * useState would and then follows later changes of the parameter, replacing the state only when the URL actually
 * carries a different filter set than the one currently applied.
 *
 * The default filters apply when the URL carries no "filters" parameter at all. A present but empty parameter is
 * how a cleared filter set is kept.
 *
 * While a monitor is applied, the URL carries a "monitor" parameter and no "filters" parameter, and the Filters
 * component loads the filters from that monitor. The hook leaves the state alone in that case.
 */
export default function useUrlFilters(fields, defaultFilters = null) {
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const raw = params.has("filters") ? params.get("filters") : null;
  const monitorApplied = params.has("monitor");

  const fromUrl = (r) => r === null ? defaultFilters : queryParametersToFilters(r, fields);

  const [filters, setFilters] = useState(() => fromUrl(raw));
  const lastRaw = useRef(raw);

  useEffect(() => {
    if (lastRaw.current === raw) {
      return;
    }

    lastRaw.current = raw;

    if (monitorApplied) {
      return;
    }

    setFilters((current) => {
      const currentRaw = current ? JSON.stringify(filtersToQueryParameters(current)) : null;
      return currentRaw === raw ? current : fromUrl(raw);
    });
  }, [raw, monitorApplied]);

  return [filters, setFilters];
}
