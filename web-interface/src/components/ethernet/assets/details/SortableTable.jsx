import React, {useState} from "react";
import ColumnSorting from "../../../shared/ColumnSorting";

export default function SortableTable({columns, rows, rowKey, defaultColumn, defaultDirection = "DESC"}) {

  const [orderColumn, setOrderColumn] = useState(defaultColumn);
  const [orderDirection, setOrderDirection] = useState(defaultDirection);

  const column = columns.find((c) => c.key === orderColumn) || columns[0];
  const direction = orderDirection === "ASC" ? 1 : -1;
  const sorted = rows.slice().sort((a, b) => {
    const x = column.value(a);
    const y = column.value(b);
    const cmp = typeof x === "string" ? x.localeCompare(y) : x - y;
    return cmp !== 0 ? cmp * direction : rowKey(a).localeCompare(rowKey(b));
  });

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        {columns.map((c) => (
          <th key={c.key} className="text-nowrap">
            {c.label}{' '}
            <ColumnSorting thisColumn={c.key}
                           orderColumn={orderColumn}
                           setOrderColumn={setOrderColumn}
                           orderDirection={orderDirection}
                           setOrderDirection={setOrderDirection} />
          </th>
        ))}
      </tr>
      </thead>
      <tbody>
      {sorted.map((row) => (
        <tr key={rowKey(row)}>
          {columns.map((c) => <td key={c.key}>{c.render(row)}</td>)}
        </tr>
      ))}
      </tbody>
    </table>
  );

}
