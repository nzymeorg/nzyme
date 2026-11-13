import {useLayoutEffect, useState} from "react";

// The size of an element in em of its own font size. Nothing on a wall can scroll, so widgets lay out what fits.
export default function useSizeEm() {

  // Held in state and not in a ref: a widget without data does not render the measured element, and the
  // measuring has to start when it shows up later.
  const [element, setElement] = useState(null);
  const [size, setSize] = useState({width: 0, height: 0});

  useLayoutEffect(() => {
    if (!element) {
      return;
    }

    const measure = () => {
      const fontSize = parseFloat(window.getComputedStyle(element).fontSize);
      if (!fontSize) {
        return;
      }

      const width = Math.round(element.clientWidth/fontSize*10)/10;
      const height = Math.round(element.clientHeight/fontSize*10)/10;

      setSize((previous) => previous.width === width && previous.height === height ? previous : {width, height});
    }

    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(element);

    return () => observer.disconnect();
  }, [element]);

  return [setElement, size];

}
