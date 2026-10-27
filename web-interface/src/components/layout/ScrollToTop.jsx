import {useEffect} from "react";
import {useLocation} from "react-router-dom";

/*
 * A full page load always started at the top of the page. Client-side navigation keeps the scroll position, so this
 * scrolls to the top whenever the path changes. Query parameter changes, such as filters or time ranges, are left alone.
 */
export default function ScrollToTop() {
  const {pathname} = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
}
