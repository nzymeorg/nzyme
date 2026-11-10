import {useEffect, useRef, useState} from "react";

export default function useTopologyRequest(key, enabled, load, delay = 0) {
  const [state, setState] = useState({key: null, data: null});
  const loadedKey = useRef(null);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (!enabled || key === null || loadedKey.current === key) {
      return undefined;
    }

    let cancelled = false;
    const run = () => loadRef.current((data) => {
      if (!cancelled) {
        loadedKey.current = key;
        setState({key: key, data: data});
      }
    });

    let timer = null;
    if (delay > 0) {
      timer = window.setTimeout(run, delay);
    } else {
      run();
    }

    return () => {
      cancelled = true;
      if (timer !== null) window.clearTimeout(timer);
    };
  }, [key, enabled, delay]);

  return state.key === key ? state.data : null;
}
