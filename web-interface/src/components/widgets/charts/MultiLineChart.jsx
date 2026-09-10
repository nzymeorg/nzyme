import React from 'react'

import Plot from 'react-plotly.js'
import Store from '../../../util/Store'
import {Absolute} from "../../shared/timerange/TimeRange";

class MultiLineChart extends React.Component {
  constructor (props) {
    super(props)

    this.state = {
      data: props.data,
    }
  }

  componentWillReceiveProps (nextProps) {
    this.setState({ data: nextProps.data })
  }

  render () {
    const data = this.state.data
    const seriesNames = this.props.seriesNames || {};

    const lightLinesPalette = [
      '#1d30d7',
      '#9467bd',
      '#e377c2',
      '#d7d130'
    ]

    const darkLinesPalette = [
      '#9baee5',
      '#b087f4',
      '#f48af1',
      '#e8c95a'
    ]

    const defaultPalette = Store.get('dark_mode') ? darkLinesPalette : lightLinesPalette

    let finalData = this.props.finalData
    if (!finalData) {
      finalData = []

      Object.keys(data).forEach((seriesName, index) => {
        const xs = []
        const ys = []

        Object.keys(data[seriesName]).forEach((key) => {
          xs.push(new Date(key))
          ys.push(data[seriesName][key])
        })

        const displayName = seriesNames[seriesName] || seriesName;

        finalData.push({
          name: displayName,
          x: xs,
          y: ys,
          type: 'scatter',
          mode: this.props.scattermode ? this.props.scattermode : 'lines',
          marker: { size: 3 },
          line: {
            width: this.props.lineWidth ? this.props.lineWidth : 2,
            shape: 'linear',
            color: this.props.colors?.[seriesName] || defaultPalette[index % defaultPalette.length]
          }
        })
      })
    }

    let allTimestamps = []
    finalData.forEach(trace => {
      allTimestamps = allTimestamps.concat(trace.x.map(d => d.getTime()))
    })

    let xRange = undefined
    const timeRange = this.props.timeRange

    if (timeRange && timeRange.type === 'relative') {
      const now = new Date()
      xRange = [new Date(now.getTime() - timeRange.minutes * 60 * 1000), now]
    } else if (timeRange && timeRange.type === 'absolute') {
      xRange = [new Date(timeRange.from), new Date(timeRange.to)]
    } else if (allTimestamps.length > 1) {
      const bufferMs = 5 * 60 * 1000 // optional
      const min = new Date(Math.min(...allTimestamps) - bufferMs)
      const max = new Date(Math.max(...allTimestamps) + bufferMs)
      xRange = [min, max]
    }

    const marginLeft = this.props.customMarginLeft ? this.props.customMarginLeft : 25
    const marginRight = this.props.customMarginRight ? this.props.customMarginRight : 0
    const marginTop = this.props.customMarginTop ? this.props.customMarginTop : 25
    const marginBottom = this.props.customMarginBottom ? this.props.customMarginBottom : 50

    const colors = {}
    if (Store.get('dark_mode')) {
      colors.background = '#1c1c22'
      colors.text = '#c4c4d4'
      colors.lines = '#33333d'
      colors.grid = '#2a2a33'
    } else {
      colors.background = '#f9f9f9'
      colors.text = '#111111'
      colors.lines = '#373737'
      colors.grid = '#e6e6e6'
    }

    return (
        <Plot
            style={{ width: '100%', height: '100%' }}
            data={finalData}
            layout={{
              height: this.props.height,
              width: this.props.width,
              font: {
                family: "'Nunito Sans', sans-serif",
                size: 12,
                color: colors.text
              },
              margin: { l: marginLeft, r: marginRight, b: marginBottom, t: marginTop, pad: 0 },
              title: { text: this.props.title },
              paper_bgcolor: colors.background,
              plot_bgcolor: colors.background,
              showlegend: true,
              dragmode: this.props.setTimeRange ? 'zoom' : false,
              clickmode: 'none',
              hovermode: this.props.disableHover ? false : 'x',
              hoverlabel: {
                font: { size: 11 },
                namelength: -1
              },
              barmode: 'stack',
              boxgap: 0,
              xaxis: {
                fixedrange: false,
                rangeslider: { visible: false },
                title: this.props.xaxistitle,
                linecolor: colors.lines,
                linewidth: 1,
                gridcolor: colors.grid,
                zeroline: false,
                range: xRange,
              },
              yaxis: {
                ticksuffix: this.props.ticksuffix ? this.props.ticksuffix : undefined,
                tickformat: this.props.tickformat ? this.props.tickformat : undefined,
                fixedrange: true,
                title: this.props.yaxistitle,
                linecolor: colors.lines,
                linewidth: 1,
                gridcolor: colors.grid,
                zeroline: false
              },
              annotations: this.props.annotations ? this.props.annotations : [],
              shapes: this.props.shapes
            }}
            config={{
              showAxisDragHandles: false,
              displayModeBar: false,
              autosize: true,
              responsive: true,
              showTips: false
            }}
            onRelayout={event => {
              if (this.props.setTimeRange) {
                const x0 = event['xaxis.range[0]']
                const x1 = event['xaxis.range[1]']
                if (x0 != null && x1 != null) {
                  this.props.setTimeRange(Absolute(new Date(x0), new Date(x1)))
                }
              }}
            }
        />
    )
  }
}

export default MultiLineChart
