import React, {useContext, useEffect, useMemo, useRef, useState} from "react";
import Store from "../../util/Store";
import TapsService from "../../services/TapsService";
import {TapContext} from "../../App";
import {arraysAreEqual} from "../../util/Tools";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";

export const enableTapSelector = (ctx) => {
  if(ctx) {
    ctx.setSelectorEnabled(true);
  }
}

export const disableTapSelector = (ctx) => {
  if(ctx) {
    ctx.setSelectorEnabled(false);
  }
}

const tapsService = new TapsService();

const NO_LOCATION = "__no_location__";

function TapSelectorCheckbox({checked, indeterminate, onToggle}) {
  const ref = useRef(null);

  useEffect(() => {
    if (ref.current) {
      ref.current.indeterminate = !!indeterminate && !checked;
    }
  }, [indeterminate, checked]);

  return (
    <input ref={ref}
           type="checkbox"
           className="form-check-input tap-selector-check"
           checked={!!checked}
           onChange={onToggle}
           onClick={(e) => e.stopPropagation()} />
  );
}

function TapSelector() {

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);

  const selectedTaps = tapContext.taps;
  const setSelectedTaps = tapContext.set;
  const [show, setShow] = useState(false);

  const [availableTaps, setAvailableTaps] = useState(null);
  const [preSelectedTaps, setPreSelectedTaps] = useState(null);

  const [hasSelectedOfflineTap, setHasSelectedOfflineTap] = useState(false);

  const [buttonText, setButtonText] = useState(null);

  const [filter, setFilter] = useState("");
  const [expandedKeys, setExpandedKeys] = useState(() => new Set());
  const searchRef = useRef(null);

  const setSelectedTapsProtected = (taps) => {
    if (taps === "*" || taps === null) {
      if (selectedTaps !== "*") {
        setSelectedTaps("*");
      }
      return;
    }

    if (!arraysAreEqual(taps, selectedTaps)) {
      setSelectedTaps(taps);
    }
  }

  useEffect(() => {
    if (organizationId == null || tenantId == null) {
      return;
    }

    const tenantKey = organizationId + ":" + tenantId;
    const tapsTenant = Store.get("selected_taps_tenant");

    /*
     * Tap selections are per tenant, so only drop the stored selection when the tenant actually
     * changes. Re-loading the same tenant (e.g. after logging back in) keeps it. If there's no
     * marker yet (first run after this shipped), adopt the current tenant without clearing so we
     * don't wipe a pre-existing selection on upgrade.
     */
    if (tapsTenant == null) {
      Store.set("selected_taps_tenant", tenantKey);
    } else if (tapsTenant !== tenantKey) {
      Store.delete("selected_taps");
      Store.set("selected_taps_tenant", tenantKey);
    }

    // Show the loading state until the new tenant's taps arrive.
    setAvailableTaps(null);
    setPreSelectedTaps(null);

    tapsService.findAllTapsHighLevel(organizationId, tenantId, function (response) {
      setAvailableTaps(response.data.taps);
    });
  }, [organizationId, tenantId])

  useEffect(() => {
    const lsTaps = Store.get("selected_taps");

    // Clean up Store if there are no taps but a previous session still has a Store.
    if (lsTaps && (availableTaps && availableTaps.length === 0)) {
      Store.delete("selected_taps");
    }

    if (lsTaps === undefined || lsTaps === null || (!Array.isArray(lsTaps) && lsTaps !== "*")) {
      /*
       * No stored selection yet: default to an empty selection so the user makes a conscious
       * choice about which taps to view, rather than implicitly selecting everything.
       */
      setSelectedTapsProtected([]);
      setPreSelectedTaps([]);

      if (availableTaps && availableTaps.length > 0) {
        setButtonText(<span className="text-warning"><i className="fa-solid fa-triangle-exclamation text-warning"></i> No Taps Selected</span>);
      } else {
        setButtonText("No Taps Configured");
      }
    } else {
      /*
       * Validate an explicit selection against the taps that actually exist for this tenant and
       * drop any that are gone (e.g. deleted since the selection was made). "*" adapts on its own,
       * so it never needs cleaning. Runs on every load, so a deleted tap can't linger.
       */
      let effectiveTaps = lsTaps;
      if (Array.isArray(lsTaps) && availableTaps) {
        const availableUuids = new Set(availableTaps.map((t) => t.uuid));
        const validTaps = lsTaps.filter((uuid) => availableUuids.has(uuid));
        if (validTaps.length !== lsTaps.length) {
          Store.set("selected_taps", validTaps);
          effectiveTaps = validTaps;
        }
      }

      setSelectedTapsProtected(effectiveTaps);
      setPreSelectedTaps(effectiveTaps);

      if (effectiveTaps === "*") {
        setButtonText("All Taps Selected");
      } else if (effectiveTaps.length > 1) {
        setButtonText(effectiveTaps.length + " Taps Selected");
      } else if (effectiveTaps.length === 1) {
        setButtonText("1 Tap Selected");
      } else {
        setButtonText("No Taps Selected");
      }
    }
  }, [availableTaps]);

  useEffect(() => {
    setHasSelectedOfflineTap(false); // Reset.

    if (selectedTaps !== null && availableTaps !== null) {
      if (availableTaps.length === 0) {
        setButtonText("No Taps Configured");
      } else {
        if (selectedTaps === "*") {
          setButtonText("All Taps Selected");

          // Is any tap currently offline?
          availableTaps.forEach(function (availableTap) {
            if (!availableTap.is_online) {
              setHasSelectedOfflineTap(true);
            }
          });
        } else {
          // Is any of the selected taps currently offline?
          selectedTaps.forEach(function (selectedTap) {
            availableTaps.forEach(function (availableTap) {
              if (availableTap.uuid === selectedTap && !availableTap.is_online) {
                setHasSelectedOfflineTap(true);
              }
            });
          });

          if (selectedTaps !== null && selectedTaps.length > 0) {
            if (selectedTaps.length > 1) {
              setButtonText(selectedTaps.length + " Taps Selected");
            } else {
              setButtonText("1 Tap Selected");
            }
          } else {
            setButtonText(<span className="text-warning"><i className="fa-solid fa-triangle-exclamation text-warning"></i> No Taps Selected</span>);
          }
        }
      }
    }
  }, [selectedTaps, availableTaps])

  useEffect(() => {
    if (selectedTaps !== null) {
      setPreSelectedTaps(selectedTaps);
    }
  }, [selectedTaps]);

  // Focus the filter when the menu opens and clear it when the menu closes.
  useEffect(() => {
    if (show) {
      const id = setTimeout(() => {
        if (searchRef.current) {
          searchRef.current.focus();
        }
      }, 0);
      return () => clearTimeout(id);
    } else {
      setFilter("");
    }
  }, [show]);

  // Flat list of every available UUID. Used to detect a "select everything" state.
  const allUuids = useMemo(() => {
    return availableTaps ? availableTaps.map((t) => t.uuid) : [];
  }, [availableTaps]);

  /*
   * Downstream treats "*" as "all taps" and aggregates the data as if multiple taps supplied
   * it. That's only correct when more than one tap actually exists, so we only ever collapse a
   * full selection to "*" when there are at least two taps. A single tap is always reported as
   * an explicit list containing that one tap, never as "*".
   */
  const canCollapseToAll = allUuids.length > 1;

  /*
   * Build the Location -> Floor -> Tap tree. Floors are nested inside locations because the
   * same floor name (e.g. "Ground Level") can exist in several locations and only means
   * something within its location. Taps with a location but no floor, and taps with no
   * location at all, are handled explicitly below.
   */
  const groups = useMemo(() => {
    if (!availableTaps) {
      return [];
    }

    const byName = (a, b) => (a.name || "").localeCompare(b.name || "");
    const locations = new Map();

    availableTaps.forEach((tap) => {
      const locKey = tap.location_name == null ? NO_LOCATION : tap.location_name;

      if (!locations.has(locKey)) {
        locations.set(locKey, {name: tap.location_name == null ? null : tap.location_name, floors: new Map(), loose: []});
      }

      const loc = locations.get(locKey);

      if (tap.floor_name == null) {
        loc.loose.push(tap);
      } else {
        if (!loc.floors.has(tap.floor_name)) {
          loc.floors.set(tap.floor_name, []);
        }
        loc.floors.get(tap.floor_name).push(tap);
      }
    });

    const locationEntries = Array.from(locations.entries()).sort((a, b) => {
      if (a[0] === NO_LOCATION) return 1;   // unassigned location always last
      if (b[0] === NO_LOCATION) return -1;
      return (a[1].name || "").localeCompare(b[1].name || "");
    });

    return locationEntries.map(([locKey, loc]) => {
      const floors = Array.from(loc.floors.entries())
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([floorName, taps]) => {
          const sorted = [...taps].sort(byName);
          return {
            key: "floor:" + locKey + ":" + floorName,
            label: floorName,
            taps: sorted,
            uuids: sorted.map((t) => t.uuid),
            offlineCount: sorted.filter((t) => !t.is_online).length
          };
        });

      const loose = [...loc.loose].sort(byName);
      const allTaps = [...floors.flatMap((f) => f.taps), ...loose];

      return {
        key: "loc:" + locKey,
        noFloorKey: "nofloor:" + locKey,
        isNoLocation: locKey === NO_LOCATION,
        name: loc.name,
        floors: floors,
        looseTaps: loose,
        looseUuids: loose.map((t) => t.uuid),
        /*
         * Only show a dedicated "No floor" sub-group when the location ALSO has real floors,
         * so we don't pointlessly nest a fully floor-less location under a "No floor" header.
         */
        showNoFloorSection: floors.length > 0 && loose.length > 0,
        uuids: allTaps.map((t) => t.uuid),
        total: allTaps.length,
        offlineCount: allTaps.filter((t) => !t.is_online).length
      };
    });
  }, [availableTaps]);

  // Apply the filter. A location/floor is kept if its name matches or any tap under it matches.
  const filteredGroups = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) {
      return groups;
    }

    const tapMatches = (tap, locName, floorName) =>
      (tap.name || "").toLowerCase().includes(q) ||
      (locName || "").toLowerCase().includes(q) ||
      (floorName || "").toLowerCase().includes(q);

    const result = [];

    groups.forEach((loc) => {
      const floors = loc.floors
        .map((f) => {
          const taps = f.taps.filter((t) => tapMatches(t, loc.name, f.label));
          return {...f, taps: taps, uuids: taps.map((t) => t.uuid), offlineCount: taps.filter((t) => !t.is_online).length};
        })
        .filter((f) => f.taps.length > 0);

      const loose = loc.looseTaps.filter((t) => tapMatches(t, loc.name, null));

      if (floors.length === 0 && loose.length === 0) {
        return;
      }

      const allTaps = [...floors.flatMap((f) => f.taps), ...loose];

      result.push({
        ...loc,
        floors: floors,
        looseTaps: loose,
        looseUuids: loose.map((t) => t.uuid),
        showNoFloorSection: floors.length > 0 && loose.length > 0,
        uuids: allTaps.map((t) => t.uuid),
        total: allTaps.length,
        offlineCount: allTaps.filter((t) => !t.is_online).length
      });
    });

    return result;
  }, [groups, filter]);

  const selectedSet = useMemo(() => {
    return preSelectedTaps === "*" ? null : new Set(preSelectedTaps);
  }, [preSelectedTaps]);

  const toggleMenu = function() {
    setShow(!show);
  }

  const isAll = preSelectedTaps === "*";

  /*
   * True when every available tap is selected, whether that's stored as "*" or, in the
   * single-tap case, as an explicit list containing that one tap. Used to drive the master
   * checkbox and its toggle so a single-tap "[uuid]" still reads as fully selected.
   */
  const allSelected = isAll || (allUuids.length > 0 && selectedSet !== null && allUuids.every((u) => selectedSet.has(u)));

  const workingSet = () => isAll ? new Set(allUuids) : new Set(preSelectedTaps);

  const commitSet = (set) => {
    if (canCollapseToAll && allUuids.every((u) => set.has(u))) {
      setPreSelectedTaps("*");
    } else {
      setPreSelectedTaps(Array.from(set));
    }
  };

  const isTapChecked = (uuid) => isAll ? true : selectedSet.has(uuid);

  // Returns {checked, indeterminate} for a group based on how many of its taps are selected.
  const groupCheckState = (uuids) => {
    if (uuids.length === 0) {
      return {checked: false, indeterminate: false};
    }
    if (isAll) {
      return {checked: true, indeterminate: false};
    }
    let selected = 0;
    uuids.forEach((u) => { if (selectedSet.has(u)) selected++; });
    if (selected === 0) return {checked: false, indeterminate: false};
    if (selected === uuids.length) return {checked: true, indeterminate: false};
    return {checked: false, indeterminate: true};
  };

  const toggleTap = (uuid) => {
    const set = workingSet();
    if (set.has(uuid)) {
      set.delete(uuid);
    } else {
      set.add(uuid);
    }
    commitSet(set);
  };

  // Select-all-or-none for a group (location, floor, or "no floor" bucket). When a filter is
  // active, `uuids` is the currently visible subset, so this acts on what the user can see.
  const toggleGroup = (uuids) => {
    if (uuids.length === 0) return;
    const set = workingSet();
    const allIn = uuids.every((u) => set.has(u));
    uuids.forEach((u) => { if (allIn) set.delete(u); else set.add(u); });
    commitSet(set);
  };

  const toggleAllTaps = () => {
    if (allSelected) {
      setPreSelectedTaps([]); // empty selection is allowed -> "No Taps Selected"
    } else if (canCollapseToAll) {
      setPreSelectedTaps("*");
    } else {
      setPreSelectedTaps([...allUuids]); // single tap: report it as itself, not "*"
    }
  };

  const masterState = allSelected
    ? {checked: true, indeterminate: false}
    : (preSelectedTaps && preSelectedTaps.length === 0
      ? {checked: false, indeterminate: false}
      : {checked: false, indeterminate: true});

  const onSelectTaps = function(e) {
    e.preventDefault();

    Store.set("selected_taps", preSelectedTaps);
    setSelectedTapsProtected(preSelectedTaps);

    setShow(false);
  }

  const filterActive = filter.trim().length > 0;
  const isExpanded = (key) => filterActive || expandedKeys.has(key);

  const toggleExpanded = (key) => {
    setExpandedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const padLeft = (level) => ({paddingLeft: 12 + level * 18});

  const renderTapRow = (tap, level) => (
    <div key={"tap:" + tap.uuid} className="tap-selector-row" style={padLeft(level)}>
      <span className="tap-selector-caret" />
      <TapSelectorCheckbox checked={isTapChecked(tap.uuid)} indeterminate={false} onToggle={() => toggleTap(tap.uuid)} />
      <span className="tap-selector-rowlabel" role="button" onClick={() => toggleTap(tap.uuid)}>
          {tap.name}
        {!tap.is_online ? <span className="text-warning"> (Offline)</span> : null}
        </span>
    </div>
  );

  if (!tapContext.selectorEnabled) {
    return (
      <div className="dropdown" title="No tap selection required on this page">
        <button className="btn btn-outline-secondary" type="button" disabled={true}>
          No Tap Selection Required
        </button>
      </div>
    )
  }

  if (availableTaps === null || selectedTaps === null || preSelectedTaps === null) {
    return (
      <div className="dropdown">
        <button className="btn btn-outline-secondary dropdown-toggle" type="button" disabled={true}>
          <i className="fa-solid fa-spinner fa-spin-pulse fa-sm"></i> Tap Selector Loading
        </button>
      </div>
    )
  }

  return (
    <React.Fragment>
      <style>{`
        .tap-selector-menu { min-width: 300px; max-width: 380px; padding-top: 8px; padding-bottom: 8px; }
        .tap-selector-row { display: flex; align-items: center; gap: 8px; padding: 5px 12px; white-space: nowrap; }
        .tap-selector-row:hover { background: rgba(0, 0, 0, .06); }
        .tap-selector-caret { flex: 0 0 16px; width: 16px; text-align: center; color: #9aa0a6; cursor: default; }
        .tap-selector-caret-btn { cursor: pointer; }
        .tap-selector-check { flex: 0 0 auto; margin: 0; cursor: pointer; }
        .tap-selector-rowlabel { flex: 1 1 auto; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
        .tap-selector-locname { font-weight: 600; }
        .tap-selector-muted { color: #9aa0a6; }
        .tap-selector-meta { color: #9aa0a6; font-size: .8em; flex: 0 0 auto; }
        .tap-selector-scroll { max-height: 320px; overflow-y: auto; overflow-x: hidden; }
      `}</style>
      <div className="dropdown">
        <button className="btn btn-outline-secondary dropdown-toggle"
                type="button"
                aria-expanded="false"
                data-bs-auto-close="false"
                onClick={toggleMenu}
                disabled={availableTaps.length === 0}>
          {buttonText}{' '}
          {hasSelectedOfflineTap ? <i className="fa-solid fa-triangle-exclamation text-warning"></i> : null }
        </button>
        <ul className="dropdown-menu tap-selector-menu" style={{display: show ? "block" : "none"}}>
          {/* Filter */}
          <li style={{padding: "0 12px 8px"}}>
            <input ref={searchRef}
                   type="text"
                   className="form-control form-control-sm"
                   placeholder="Filter taps, floors, locations…"
                   value={filter}
                   onChange={(e) => setFilter(e.target.value)}
                   onClick={(e) => e.stopPropagation()} />
          </li>

          {/* All taps master toggle */}
          <li>
            <div className="tap-selector-row" style={padLeft(0)}>
              <span className="tap-selector-caret" />
              <TapSelectorCheckbox checked={masterState.checked} indeterminate={masterState.indeterminate} onToggle={toggleAllTaps} />
              <span className="tap-selector-rowlabel tap-selector-locname" role="button" onClick={toggleAllTaps}>
                All Taps
              </span>
              <span className="tap-selector-meta">{allUuids.length}</span>
            </div>
          </li>

          <li><hr className="dropdown-divider" /></li>

          {/* Location -> Floor -> Tap tree */}
          <li>
            <div className="tap-selector-scroll">
              {filteredGroups.length === 0 ? (
                <div className="tap-selector-row" style={{...padLeft(0), color: "#9aa0a6"}}>
                  <span className="tap-selector-caret" />
                  <span className="tap-selector-rowlabel">{filterActive ? "No matches" : "No taps configured"}</span>
                </div>
              ) : filteredGroups.map((loc) => {
                const locState = groupCheckState(loc.uuids);
                const looseState = groupCheckState(loc.looseUuids);
                const locOpen = isExpanded(loc.key);
                const locName = loc.isNoLocation ? "No location" : loc.name;

                return (
                  <React.Fragment key={loc.key}>
                    {/* Location header */}
                    <div className="tap-selector-row" style={padLeft(0)}>
                        <span className="tap-selector-caret tap-selector-caret-btn" onClick={() => toggleExpanded(loc.key)}>
                          <i className={"fa-solid " + (locOpen ? "fa-chevron-down" : "fa-chevron-right")} style={{fontSize: ".75em"}} />
                        </span>
                      <TapSelectorCheckbox checked={locState.checked} indeterminate={locState.indeterminate} onToggle={() => toggleGroup(loc.uuids)} />
                      <span className={"tap-selector-rowlabel " + (loc.isNoLocation ? "fst-italic tap-selector-muted" : "tap-selector-locname")}
                            role="button" onClick={() => toggleExpanded(loc.key)}>
                          <i className="fa-solid fa-location-dot" style={{marginRight: 6, color: "#9aa0a6"}} />
                        {locName}
                        </span>
                      <span className="tap-selector-meta">{loc.total}</span>
                      {loc.offlineCount > 0
                        ? <i className="fa-solid fa-triangle-exclamation text-warning" style={{marginLeft: 6}} title={loc.offlineCount + " offline"} />
                        : null}
                    </div>

                    {locOpen ? (
                      <React.Fragment>
                        {/* Named floors */}
                        {loc.floors.map((floor) => {
                          const flState = groupCheckState(floor.uuids);
                          const flOpen = isExpanded(floor.key);
                          return (
                            <React.Fragment key={floor.key}>
                              <div className="tap-selector-row" style={padLeft(1)}>
                                      <span className="tap-selector-caret tap-selector-caret-btn" onClick={() => toggleExpanded(floor.key)}>
                                        <i className={"fa-solid " + (flOpen ? "fa-chevron-down" : "fa-chevron-right")} style={{fontSize: ".75em"}} />
                                      </span>
                                <TapSelectorCheckbox checked={flState.checked} indeterminate={flState.indeterminate} onToggle={() => toggleGroup(floor.uuids)} />
                                <span className="tap-selector-rowlabel" role="button" onClick={() => toggleExpanded(floor.key)}>
                                        <i className="fa-solid fa-layer-group" style={{marginRight: 6, color: "#9aa0a6"}} />
                                  {floor.label}
                                      </span>
                                <span className="tap-selector-meta">{floor.taps.length}</span>
                                {floor.offlineCount > 0
                                  ? <i className="fa-solid fa-triangle-exclamation text-warning" style={{marginLeft: 6}} title={floor.offlineCount + " offline"} />
                                  : null}
                              </div>
                              {flOpen ? floor.taps.map((t) => renderTapRow(t, 2)) : null}
                            </React.Fragment>
                          );
                        })}

                        {/* Floor-less taps inside a location that also has real floors */}
                        {loc.showNoFloorSection ? (
                          <React.Fragment>
                            <div className="tap-selector-row" style={padLeft(1)}>
                                    <span className="tap-selector-caret tap-selector-caret-btn" onClick={() => toggleExpanded(loc.noFloorKey)}>
                                      <i className={"fa-solid " + (isExpanded(loc.noFloorKey) ? "fa-chevron-down" : "fa-chevron-right")} style={{fontSize: ".75em"}} />
                                    </span>
                              <TapSelectorCheckbox checked={looseState.checked} indeterminate={looseState.indeterminate} onToggle={() => toggleGroup(loc.looseUuids)} />
                              <span className="tap-selector-rowlabel fst-italic tap-selector-muted" role="button" onClick={() => toggleExpanded(loc.noFloorKey)}>
                                      No floor
                                    </span>
                              <span className="tap-selector-meta">{loc.looseTaps.length}</span>
                            </div>
                            {isExpanded(loc.noFloorKey) ? loc.looseTaps.map((t) => renderTapRow(t, 2)) : null}
                          </React.Fragment>
                        ) : (
                          /* Location has no real floors: list its taps directly under it */
                          loc.looseTaps.map((t) => renderTapRow(t, 1))
                        )}
                      </React.Fragment>
                    ) : null}
                  </React.Fragment>
                );
              })}
            </div>
          </li>

          <li className="tap-selector-actions" style={{padding: "8px 12px 0"}}>
            <button className="btn btn-primary btn-sm tap-selector-select w-100" onClick={onSelectTaps}>
              Select Taps
            </button>
          </li>
        </ul>
      </div>
    </React.Fragment>
  )

}

export default TapSelector;