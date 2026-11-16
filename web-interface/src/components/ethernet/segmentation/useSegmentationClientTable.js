import {useState} from "react";
import {pageRows, pageWithin, rowMatches} from "./SegmentationClientTable";
import {sortRows} from "../assets/details/AssetProfileHelpers";

export default function useSegmentationClientTable(rows, fields, columns, defaultOrderColumn,
                                                   defaultOrderDirection = "ASC", keep = undefined, perPage = 25) {

  const [filters, setFilters] = useState({});
  const [orderColumn, setOrderColumn] = useState(defaultOrderColumn);
  const [orderDirection, setOrderDirection] = useState(defaultOrderDirection);
  const [page, setPage] = useState(1);

  const matching = sortRows(
    rows.filter((row) => (keep && keep(row)) || rowMatches(row, filters, fields)), orderColumn, orderDirection, columns
  );

  const shownPage = pageWithin(page, matching.length, perPage);

  return {
    filters: filters,
    setFilters: (newFilters) => { setPage(1); setFilters(newFilters ? newFilters : {}); },
    orderColumn: orderColumn,
    setOrderColumn: (column) => { setPage(1); setOrderColumn(column); },
    orderDirection: orderDirection,
    setOrderDirection: (direction) => { setPage(1); setOrderDirection(direction); },
    page: shownPage,
    setPage: setPage,
    perPage: perPage,
    total: matching.length,
    matching: matching,
    rows: pageRows(matching, shownPage, perPage),
    showRow: (isRow) => {
      const position = matching.findIndex(isRow);
      if (position >= 0) {
        setPage(Math.floor(position / perPage) + 1);
      }
    }
  };
}
