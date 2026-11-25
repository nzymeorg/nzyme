import {useEffect, useRef} from "react";
import {useLocation} from "react-router-dom";

const INTERVAL_MS = 15000;

export default function useBackgroundRefresh(refresh, deps = [], paused = false) {
  const {pathname} = useLocation();
  const lastPathname = useRef(pathname);

  useEffect(() => {
    if (paused) {
      return;
    }

    const controller = new AbortController();

    if (lastPathname.current !== pathname) {
      lastPathname.current = pathname;
      refresh(controller.signal);
    }

    const interval = setInterval(() => refresh(controller.signal), INTERVAL_MS);

    return () => {
      clearInterval(interval);
      controller.abort();
    }
  }, [pathname, paused, ...deps]);
}
