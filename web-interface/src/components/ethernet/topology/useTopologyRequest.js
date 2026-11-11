import {useEffect, useRef, useState} from "react";

export default function useTopologyRequest(key, enabled, load) {
  const [state, setState] = useState({key: null, data: null});
  const loadedKey = useRef(null);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (!enabled || key === null || loadedKey.current === key) {
      return undefined;
    }

    let cancelled = false;
    loadRef.current((data) => {
      if (!cancelled) {
        loadedKey.current = key;
        setState({key: key, data: data});
      }
    });

    return () => {
      cancelled = true;
    };
  }, [key, enabled]);

  return state.key === key ? state.data : null;
}
