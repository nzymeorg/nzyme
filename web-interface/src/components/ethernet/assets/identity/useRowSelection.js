import {useState} from "react";

/*
 * Checkbox selection for a paginated table. Rows that left the page are dropped from the selection on read, so
 * the table needs no effect to reset it when the data changes.
 */
export default function useRowSelection(rows) {

  const [selected, setSelected] = useState([]);

  const uuids = rows.map((r) => r.uuid);
  const current = selected.filter((uuid) => uuids.includes(uuid));
  const allSelected = uuids.length > 0 && current.length === uuids.length;

  const toggle = (uuid) => {
    if (current.includes(uuid)) {
      setSelected(current.filter((s) => s !== uuid));
    } else {
      setSelected([...current, uuid]);
    }
  }

  const toggleAll = () => setSelected(allSelected ? [] : uuids);

  const clear = () => setSelected([]);

  return {selected: current, allSelected, toggle, toggleAll, clear};

}
