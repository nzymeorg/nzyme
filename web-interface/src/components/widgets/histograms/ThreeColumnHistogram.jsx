import React, {useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";

import SimpleBarChart from "../charts/SimpleBarChart";
import HistogramHeader from "./HistogramHeader";

import Papa from "papaparse";
import {EXPORT_TYPE_CSV, EXPORT_TYPE_JSON, EXPORT_TYPE_TEXT} from "./ExportTypes";
import {MODE_BAR_CHART, MODE_EXPORT, MODE_TABLE} from "./HistogramModes";
import HistogramValue from "./HistogramValue";
import Store from "../../../util/Store";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import ColumnSorting from "../../shared/ColumnSorting";

function ThreeColumnHistogram(props) {

  const data = props.data;
  const columnTitles = props.columnTitles;
  const customChartMarginLeft = props.customChartMarginLeft;
  const limit = props.limit;
  const setLimit = props.setLimit;
  const highlightValue = props.highlightValue;
  const chartColumnValue = props.customChartColumnValue ? props.customChartColumnValue : "column_three";
  const chartColumnValueField = props.customChartColumnValueField;

  const columnFilterElements = props.columnFilterElements ? props.columnFilterElements : [];

  const orderColumnOneIsKey = props.orderColumnOneIsKey === undefined ? false : props.orderColumnOneIsKey;
  const orderColumn = props.orderColumn;
  const setOrderColumn = props.setOrderColumn;
  const orderDirection = props.orderDirection;
  const setOrderDirection = props.setOrderDirection;

  const [mode, setMode] = useState(MODE_TABLE);

  const [exportType, setExportType] = useState(EXPORT_TYPE_JSON);
  const [exportCopiedToClipboard, setExportCopiedToClipboard] = useState(false);

  const formatBarChartData = function() {
    const x = [];
    const y = [];

    data.values.toReversed().forEach((d) => {
      y.push(d.key_summary);
      x.push(chartColumnValueField ? d[chartColumnValue].value[chartColumnValueField] : d[chartColumnValue].value);
    });

    return [
      {
        x: x,
        y: y,
        type: 'bar',
        marker: { color: Store.get('dark_mode') ? '#e6e6e6' : '#1d30d7' },
        orientation: "h"
      }
    ]
  }

  const header = () => {
    return <HistogramHeader limit={limit} setLimit={setLimit}
                            mode={mode} setMode={setMode}
                            showBarChart={data.show_bar_chart}
                            count={data.total} />
  }

  const filterElement = (element, value) => {
    if (!element || !value.value) {
      return null;
    }

    /*
     * If the value is not a simple string or number, but, for example a L4Address, we can pass a "sub field" to
     * select a field on top of the value element.
     */
    let selectedValue = element.valueSubField ? value.value[element.valueSubField] : value.value;

    return <FilterValueIcon setFilters={element.setFilters}
                            fields={element.fields}
                            field={element.field}
                            value={selectedValue} />
  }

  const orderElement = (column) => {
    if (!orderColumn || !setOrderColumn || !orderDirection || !setOrderDirection) {
      return null;
    }

    return <ColumnSorting thisColumn={column}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const formatExport = () => {
    const formatted = [];

    data.values.forEach((d) => {
      formatted.push({
        [columnTitles[0]]: d.column_one.value,
        [columnTitles[1]]: d.column_two.value,
        [columnTitles[2]]: d.column_three.value,
      });
    });

    return formatted;
  }

  const formatJsonExport = () => {
    return JSON.stringify(formatExport(),null, 2)
  }

  const formatCsvExport = () => {
    return Papa.unparse(formatExport(), {quotes: true});
  }

  const formatTextExport = () => {
    let result = "";

    data.values.forEach((d) => {
      result += d.key_summary + ": " + d.column_three.value + "\n"
    });

    return result;
  }

  const exportData = () => {
    switch (exportType) {
      case EXPORT_TYPE_JSON:
        return formatJsonExport();
      case EXPORT_TYPE_CSV:
        return formatCsvExport()
      case EXPORT_TYPE_TEXT:
        return formatTextExport()
    }
  }

  const exportToClipboard = () => {
    navigator.clipboard.writeText(exportData()).then(() => setExportCopiedToClipboard(true));
  }

  const copiedToClipboard = () => {
    return <i className="fa-solid fa-check text-success" style={{position: "relative", top: 3, right: -7}}></i>
  }

  if (!data) {
    return <LoadingSpinner />
  }

  if (mode === MODE_TABLE) {
    return (
        <React.Fragment>
          {header()}

          <table className="table table-sm table-hover table-striped mb-0 mt-3">
            <thead>
            <tr>
              <th>#</th>
              <th>{columnTitles[0]} {orderElement(orderColumnOneIsKey ? "key" : "value1")}</th>
              <th>{columnTitles[1]} {orderElement(orderColumnOneIsKey ? "value1" : "value2")}</th>
              <th>{columnTitles[2]} {orderElement(orderColumnOneIsKey ? "value2" : "value3")}</th>
            </tr>
            </thead>
            <tbody>
            {data.values.map((d, i) => {
              return (
                  <tr key={i}>
                    <td className="text-muted">{i+1}</td>
                    <td>
                      <HistogramValue value={d.column_one}
                                      filterElement={filterElement(columnFilterElements[0], d.column_one)}
                                      highlightValue={highlightValue} />
                    </td>
                    <td>
                      <HistogramValue value={d.column_two}
                                      filterElement={filterElement(columnFilterElements[1], d.column_two)}
                                      highlightValue={highlightValue} />
                    </td>
                    <td>
                      <HistogramValue value={d.column_three}
                                      filterElement={filterElement(columnFilterElements[2], d.column_three)}
                                      highlightValue={highlightValue} />
                    </td>
                  </tr>
              )
            })}
            </tbody>
          </table>
        </React.Fragment>
    )
  }

  if (mode === MODE_BAR_CHART) {
    return (
        <React.Fragment>
          {header()}

          <div className="mt-3">
            <SimpleBarChart
                height={100+(data.values.length*30)}
                customMarginBottom={25}
                customMarginLeft={customChartMarginLeft ? customChartMarginLeft : 110}
                finalData={formatBarChartData()}
                hovermode="y" />
          </div>
        </React.Fragment>
    )
  }

  if (mode === MODE_EXPORT) {
    return (
        <React.Fragment>
          {header()}

          <div className="mt-3">
            <textarea className="histogram-data-export form-control" value={exportData()} readOnly={true} />

            <div className="btn-group mt-1" style={{marginRight: 2}}>
              <button className="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown">
                Format: {exportType}
              </button>
              <ul className="dropdown-menu">
                <li>
                  <a className="dropdown-item" href="#"
                     onClick={(e) => {e.preventDefault(); setExportType(EXPORT_TYPE_JSON)}}>
                    JSON
                  </a>
                </li>
                <li>
                  <a className="dropdown-item" href="#"
                     onClick={(e) => {e.preventDefault(); setExportType(EXPORT_TYPE_CSV)}}>
                    CSV
                  </a>
                </li>
                <li>
                  <a className="dropdown-item" href="#"
                     onClick={(e) => {e.preventDefault(); setExportType(EXPORT_TYPE_TEXT)}}>
                    Text
                  </a>
                </li>
              </ul>
            </div>

            <button className="btn btn-sm btn-outline-secondary mt-1" onClick={exportToClipboard}>
              Copy to clipboard
            </button>

            {exportCopiedToClipboard ? copiedToClipboard() : null}
          </div>
        </React.Fragment>
    )
  }

}

export default ThreeColumnHistogram;