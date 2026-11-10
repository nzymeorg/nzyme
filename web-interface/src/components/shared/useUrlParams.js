import {useCallback, useMemo} from "react";
import {useLocation, useNavigate} from "react-router-dom";

export default function useUrlParams(defaults) {
  const location = useLocation();
  const navigate = useNavigate();

  // The defaults are expected to be a literal, so they are compared by content, not by identity.
  const defaultsKey = JSON.stringify(defaults);

  const values = useMemo(() => {
    const params = new URLSearchParams(location.search);
    const result = {};
    Object.keys(defaults).forEach((key) => {
      result[key] = params.has(key) ? params.get(key) : defaults[key];
    });
    return result;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search, defaultsKey]);

  const setValues = useCallback((patch) => {
    // Read from the window, not from the render: several updates can happen before the next render.
    const params = new URLSearchParams(window.location.search);
    Object.keys(patch).forEach((key) => {
      const value = patch[key];
      if (value === null || value === undefined || value === defaults[key]) {
        params.delete(key);
      } else {
        params.set(key, String(value));
      }
    });

    const search = params.toString();
    if (search !== new URLSearchParams(window.location.search).toString()) {
      navigate({search: search}, {replace: true});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate, defaultsKey]);

  return [values, setValues];
}
