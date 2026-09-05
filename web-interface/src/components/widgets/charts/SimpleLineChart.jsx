import React from 'react'
import Plot from 'react-plotly.js'
import Store from '../../../util/Store'
import { Absolute } from "../../shared/timerange/TimeRange";

class SimpleLineChart extends React.Component {
  constructor (props) {
    super(props)

    this.state = {
      data: props.data
    }
  }

  componentWillReceiveProps (nextProps) {
    this.setState({ data: nextProps.data })
  }

  render () {
    const x = []
    const y = []

    const data = this.state.data

    let finalData = this.props.finalData
    if (!finalData) {
      const sortedKeys = Object.keys(data).sort(
        (a, b) => new Date(a).getTime() - new Date(b).getTime()
      )
      const times = sortedKeys.map(k => new Date(k))

      let bucketSize = this.props.bucketSize
      if (!bucketSize && times.length > 1) {
        const gcd = (a, b) => (b === 0 ? a : gcd(b, a % b))
        let g = 0
        for (let i = 1; i < times.length; i++) {
          const gap = times[i].getTime() - times[i - 1].getTime()
          if (gap > 0) g = gcd(g, gap)
        }
        bucketSize = g > 0 ? g : undefined
      }

      for (let i = 0; i < sortedKeys.length; i++) {
        const t = times[i]

        if (bucketSize && i > 0) {
          const prev = times[i - 1].getTime()
          if (t.getTime() - prev > bucketSize * 1.5) {
            x.push(new Date(prev + bucketSize))
            y.push(null)
          }
        }

        x.push(t)
        y.push(data[sortedKeys[i]])
      }

      const lineColor = Store.get('dark_mode') ? '#f9f9f9' : '#1d30d7'
      const mode = this.props.scattermode ? this.props.scattermode : 'lines'

      const hoverValue = this.props.tickformat ? `%{y:${this.props.tickformat}}` : '%{y}'
      const hoverTemplate = `${hoverValue}${this.props.ticksuffix ? this.props.ticksuffix : ''}<extra></extra>`

      finalData = [
        {
          x: x,
          y: y,
          type: 'scatter',
          mode: mode,
          connectgaps: false,
          marker: { size: 3 },
          hovertemplate: hoverTemplate,
          line: {
            width: this.props.lineWidth ? this.props.lineWidth : 2,
            shape: 'linear',
            color: lineColor
          }
        }
      ]

      if (mode.includes('lines') && !mode.includes('markers')) {
        const isoX = []
        const isoY = []
        for (let i = 0; i < y.length; i++) {
          if (y[i] == null) continue
          const prevOk = i > 0 && y[i - 1] != null
          const nextOk = i < y.length - 1 && y[i + 1] != null
          if (!prevOk && !nextOk) {
            isoX.push(x[i])
            isoY.push(y[i])
          }
        }

        if (isoX.length > 0) {
          finalData.push({
            x: isoX,
            y: isoY,
            type: 'scatter',
            mode: 'markers',
            hoverinfo: 'skip',
            marker: {
              size: (this.props.lineWidth ? this.props.lineWidth : 2) + 3,
              color: lineColor
            }
          })
        }
      }
    }

    let xRange = undefined
    const realTimes = x.filter((_, idx) => y[idx] != null)
    if (realTimes.length > 1) {
      const min = new Date(Math.min(...realTimes.map(d => d.getTime())))
      const max = new Date(Math.max(...realTimes.map(d => d.getTime())))
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

    const interactive = Boolean(this.props.setTimeRange)

    const hovermode = this.props.disableHover
      ? false
      : (this.props.hovermode ?? (interactive ? "x" : "closest"))

    const horizontalLineShapes = (this.props.horizontalLines ?? [])
      .filter(l => l && typeof l.y === 'number' && Number.isFinite(l.y))
      .map(l => ({
        type: 'line',
        xref: 'paper',
        x0: 0,
        x1: 1,
        yref: 'y',
        y0: l.y,
        y1: l.y,
        line: {
          color: l.color ?? (Store.get('dark_mode') ? '#f9f9f9' : '#111111'),
          width: l.width ?? 1,
          dash: l.dash ?? 'dash'
        },
        opacity: l.opacity ?? 1
      }))

    const shapes = [
      ...(this.props.shapes ?? []),
      ...horizontalLineShapes
    ]

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
          showlegend: false,
          dragmode: interactive ? 'zoom' : false,
          clickmode: 'none',
          hovermode: hovermode,
          hoverlabel: {
            font: { size: 11 },
            namelength: -1
          },
          barmode: 'stack',
          boxgap: 0,
          xaxis: {
            fixedrange: !interactive,
            rangeslider: { visible: false },
            title: this.props.xaxistitle,
            linecolor: colors.lines,
            linewidth: 1,
            gridcolor: colors.grid,
            zeroline: false,
            range: xRange
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
          shapes: shapes
        }}
        config={{
          showAxisDragHandles: false,
          displayModeBar: false,
          autosize: true,
          responsive: true,
          showTips: false,
          scrollZoom: false
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

export default SimpleLineChart