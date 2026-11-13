import React, {useEffect, useMemo, useRef, useState} from "react";
import {TIME_ZONES} from "./TimeZones";

const normalize = (text) => text.toLowerCase().replace(/[_/]/g, " ").trim();

const ZONES = TIME_ZONES.map((zone) => {
  return {zone: zone, text: normalize(zone), place: normalize(zone.substring(zone.lastIndexOf("/") + 1))};
});

// Every word has to occur somewhere in the name. Zones whose place starts with the first word are listed first.
const search = (query) => {
  const words = normalize(query).split(/\s+/).filter((word) => word !== "");
  if (words.length === 0) {
    return ZONES;
  }

  const matches = ZONES.filter((entry) => words.every((word) => entry.text.includes(word)));
  const leading = matches.filter((entry) => entry.place.startsWith(words[0]));

  return leading.concat(matches.filter((entry) => !leading.includes(entry)));
}

export default function TimeZoneSelect({id, value, onChange}) {

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);

  const input = useRef(null);
  const activeItem = useRef(null);

  const matches = useMemo(() => search(query), [query]);

  useEffect(() => {
    if (open && activeItem.current) {
      activeItem.current.scrollIntoView({block: "nearest"});
    }
  }, [open, active, matches]);

  // With the name selected, typing starts a new search.
  useEffect(() => {
    if (!open && input.current && document.activeElement === input.current) {
      input.current.select();
    }
  }, [open]);

  const show = () => {
    setQuery("");
    setActive(Math.max(0, ZONES.findIndex((entry) => entry.zone === value)));
    setOpen(true);
  }

  const pick = (zone) => {
    setOpen(false);
    if (zone !== value) {
      onChange(zone);
    }
  }

  const onKeyDown = (e) => {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        if (open) {
          setActive(Math.min(active + 1, matches.length - 1));
        } else {
          show();
        }
        break;
      case "ArrowUp":
        e.preventDefault();
        setActive(Math.max(active - 1, 0));
        break;
      case "Enter":
        // Never submits the form this is part of.
        e.preventDefault();
        if (open && matches[active]) {
          pick(matches[active].zone);
        }
        break;
      case "Escape":
        if (open) {
          e.preventDefault();
          setOpen(false);
        }
        break;
      default:
    }
  }

  return (
    <div className="position-relative">
      <input type="text" className="form-control" id={id} ref={input} role="combobox" autoComplete="off"
             spellCheck={false} aria-expanded={open} aria-controls={id + "-list"} aria-autocomplete="list"
             aria-activedescendant={open && matches[active] ? id + "-option-" + active : undefined}
             placeholder={open ? value : undefined}
             value={open ? query : value}
             onFocus={show}
             onClick={() => { if (!open) show(); }}
             onBlur={() => setOpen(false)}
             onChange={(e) => { setQuery(e.target.value); setActive(0); setOpen(true); }}
             onKeyDown={onKeyDown} />

      {open ?
        <ul className="dropdown-menu show w-100" id={id + "-list"} role="listbox"
            style={{maxHeight: 280, overflowY: "auto"}}>
          {matches.length === 0 ? <li className="dropdown-item-text text-muted">No time zone matches.</li> : null}

          {matches.map((entry, i) => {
            return (
              <li key={entry.zone} role="option" id={id + "-option-" + i} aria-selected={entry.zone === value}
                  ref={i === active ? activeItem : null}>
                {/* The pointer must not take the focus away, or the list closes before the click arrives. */}
                <button type="button" tabIndex={-1} className={"dropdown-item" + (i === active ? " active" : "")}
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => pick(entry.zone)}>
                  {entry.zone}
                </button>
              </li>
            )
          })}
        </ul> : null}
    </div>
  )

}
