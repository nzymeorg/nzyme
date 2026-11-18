import {useEffect, useRef} from "react";

const POLL_MS = 3000;
const MAX_POLL_MS = 60000;

/*
 * Calls check every few seconds for as long as pending is set and the tab is shown. check is passed a function to
 * call when its request came back, which says whether the answer is still wanted. The REST client calls nothing
 * back without a response, so a node that does not answer is asked less and less often.
 */
export default function useBackfillPoll(key, pending, check) {
  const checkRef = useRef(check);

  useEffect(() => {
    checkRef.current = check;
  });

  useEffect(() => {
    if (!pending) {
      return undefined;
    }

    let stopped = false;
    let answered = true;
    let delay = POLL_MS;
    let timer = null;

    const tick = () => {
      if (!document.hidden) {
        delay = answered ? POLL_MS : Math.min(delay * 2, MAX_POLL_MS);
        answered = false;
        checkRef.current(() => {
          answered = true;
          return !stopped;
        });
      }

      timer = setTimeout(tick, delay);
    };

    timer = setTimeout(tick, POLL_MS);

    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [key, pending]);
}
