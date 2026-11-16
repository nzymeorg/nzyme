import {compareNames} from "./SegmentationOverviewData";

/*
 * Boxes sit on one grid that all lanes share. Between its rows and columns run channels, and every link is routed
 * through them.
 */

const PADDING = 10;
const EDGE_CHANNEL = 24;
const LANE_PADDING_X = 30;
const LANE_PADDING_TOP = 18;
const LANE_PADDING_BOTTOM = 14;
const LANE_GAP = 30;
const ROW_GAP = 32;
const MIN_COLUMN_GAP = 30;
const MAX_COLUMN_GAP = 72;
const MIN_BOX_WIDTH = 132;
const MAX_BOX_WIDTH = 168;
const MAX_GAP_BONUS = 36;
const TRACK_MARGIN = 6;
const MAX_TRACK_SPACING = 5;
const TRACK_CLEARANCE = 8;
const PORT_MARGIN = 10;
const CORNER = 7;
const BOX_HEIGHT = 42;
const ARROW_LENGTH = 7;

const round = (n) => Math.round(n * 10) / 10;

export function placeBoxes(groups, width, height = 0) {
  const lanesByKey = new Map();
  groups.forEach((group) => {
    if (!lanesByKey.has(group.lane.key)) lanesByKey.set(group.lane.key, {...group.lane, groups: []});
    lanesByKey.get(group.lane.key).groups.push(group);
  });
  const lanes = Array.from(lanesByKey.values()).sort((a, b) => a.rank - b.rank || compareNames(a.title, b.title));

  const largest = lanes.reduce((max, lane) => Math.max(max, lane.groups.length), 1);
  const room = Math.max(MIN_BOX_WIDTH, width - 2 * PADDING - 2 * LANE_PADDING_X);
  const fitting = Math.max(1, Math.floor((room + MIN_COLUMN_GAP) / (MIN_BOX_WIDTH + MIN_COLUMN_GAP)));
  const columns = Math.min(fitting, largest);
  const boxWidth = Math.min(MAX_BOX_WIDTH, Math.floor((room - (columns - 1) * MIN_COLUMN_GAP) / columns));
  const columnGap = columns > 1
      ? Math.max(MIN_COLUMN_GAP, Math.min(MAX_COLUMN_GAP, Math.floor((room - columns * boxWidth) / (columns - 1))))
      : 0;
  const gridWidth = columns * boxWidth + (columns - 1) * columnGap;
  const totalWidth = Math.max(width, gridWidth + 2 * PADDING + 2 * LANE_PADDING_X);
  const left = Math.round((totalWidth - gridWidth) / 2);
  const columnX = (column) => left + column * (boxWidth + columnGap);

  const rowCount = lanes.reduce((sum, lane) => sum + Math.ceil(lane.groups.length / columns), 0);
  const natural = 2 * PADDING + 2 * EDGE_CHANNEL + rowCount * BOX_HEIGHT
      + lanes.length * (LANE_PADDING_TOP + LANE_PADDING_BOTTOM)
      + (rowCount - lanes.length) * ROW_GAP + Math.max(0, lanes.length - 1) * LANE_GAP;
  const bonus = Math.max(0, Math.min(MAX_GAP_BONUS, Math.floor((height - natural) / (rowCount + 1))));
  const edgeChannel = EDGE_CHANNEL + bonus;
  const laneGap = LANE_GAP + bonus;
  const rowGap = ROW_GAP + bonus;

  const boxes = new Map();
  const rows = [];
  let y = PADDING + edgeChannel;

  lanes.forEach((lane) => {
    lane.x = left - LANE_PADDING_X;
    lane.width = gridWidth + 2 * LANE_PADDING_X;
    lane.y = y;
    y += LANE_PADDING_TOP;

    for (let r = 0; r * columns < lane.groups.length; r++) {
      const members = lane.groups.slice(r * columns, (r + 1) * columns);
      const first = Math.floor((columns - members.length) / 2);
      if (r > 0) y += rowGap;

      const row = {index: rows.length, y: y, lane: lane};
      rows.push(row);

      members.forEach((group, i) => {
        boxes.set(group.id, {
          group: group,
          lane: lane,
          row: row.index,
          column: first + i,
          x: columnX(first + i),
          y: y,
          width: boxWidth,
          height: BOX_HEIGHT
        });
      });

      y += BOX_HEIGHT;
    }

    y += LANE_PADDING_BOTTOM;
    lane.height = y - lane.y;
    y += laneGap;
  });

  const bottom = y - laneGap;

  // The channel above every row, and one below the last.
  const horizontal = [];
  for (let r = 0; r <= rows.length; r++) {
    const above = r > 0 ? rows[r - 1] : null;
    const below = r < rows.length ? rows[r] : null;

    if (!above && !below) {
      horizontal.push({from: PADDING, to: PADDING + edgeChannel});
    } else if (!above) {
      horizontal.push({from: PADDING + 2, to: below.lane.y - TRACK_MARGIN});
    } else if (!below) {
      horizontal.push({from: bottom + TRACK_MARGIN, to: bottom + edgeChannel - 2});
    } else if (above.lane === below.lane) {
      horizontal.push({from: above.y + BOX_HEIGHT + TRACK_MARGIN + 1, to: below.y - TRACK_MARGIN - 1});
    } else {
      horizontal.push({from: above.lane.y + above.lane.height + TRACK_MARGIN, to: below.lane.y - TRACK_MARGIN});
    }
  }

  // The channel left of every column, and one right of the last.
  const vertical = [];
  for (let c = 0; c <= columns; c++) {
    if (c === 0) {
      vertical.push({from: left - LANE_PADDING_X + TRACK_MARGIN + 1, to: left - TRACK_MARGIN - 1});
    } else if (c === columns) {
      vertical.push({from: left + gridWidth + TRACK_MARGIN + 1, to: left + gridWidth + LANE_PADDING_X - TRACK_MARGIN - 1});
    } else {
      vertical.push({from: columnX(c - 1) + boxWidth + TRACK_MARGIN + 1, to: columnX(c) - TRACK_MARGIN - 1});
    }
  }

  return {
    width: totalWidth,
    height: bottom + edgeChannel + PADDING,
    lanes: lanes,
    boxes: boxes,
    horizontal: horizontal,
    vertical: vertical
  };
}

const middle = (channel) => (channel.from + channel.to) / 2;

const assignTracks = (stretches) => {
  const ends = [];
  stretches.slice().sort((a, b) => a.lo - b.lo || a.hi - b.hi).forEach((stretch) => {
    let track = ends.findIndex((end) => end + TRACK_CLEARANCE <= stretch.lo);
    if (track < 0) {
      track = ends.length;
      ends.push(stretch.hi);
    } else {
      ends[track] = stretch.hi;
    }
    stretch.track = track;
  });

  return ends.length;
}

const trackPosition = (channel, track, count) => {
  const spacing = Math.min(MAX_TRACK_SPACING, (channel.to - channel.from) / Math.max(1, count));
  return middle(channel) + (track - (count - 1) / 2) * spacing;
}

const chamferedPath = (points) => {
  const corners = points.filter((p, i) => i === 0
      || Math.abs(p[0] - points[i - 1][0]) > 0.05 || Math.abs(p[1] - points[i - 1][1]) > 0.05);
  if (corners.length < 2) return "";

  let d = "M" + round(corners[0][0]) + "," + round(corners[0][1]);
  for (let i = 1; i < corners.length - 1; i++) {
    const [px, py] = corners[i - 1];
    const [cx, cy] = corners[i];
    const [nx, ny] = corners[i + 1];
    const before = Math.hypot(cx - px, cy - py);
    const after = Math.hypot(nx - cx, ny - cy);
    const r = Math.min(CORNER, before / 2, after / 2);

    d += "L" + round(cx - (cx - px) / before * r) + "," + round(cy - (cy - py) / before * r)
        + "L" + round(cx + (nx - cx) / after * r) + "," + round(cy + (ny - cy) / after * r);
  }

  const last = corners[corners.length - 1];
  return d + "L" + round(last[0]) + "," + round(last[1]);
}

/*
 * In the same row a link runs below the boxes from left to right and above them from right to left, so that the
 * two directions of a pair never share a path.
 */
export function routeLinks(layout, cells) {
  const links = [];

  cells.forEach((cell) => {
    if (cell.inside) return;

    const a = layout.boxes.get(cell.source.id);
    const b = layout.boxes.get(cell.destination.id);
    if (!a || !b) return;

    const link = {id: cell.id, cell: cell, a: a, b: b, first: null, through: null, second: null};

    if (a.row === b.row) {
      link.exit = b.column > a.column ? "bottom" : "top";
      link.enter = link.exit;
      link.first = {channel: link.exit === "bottom" ? a.row + 1 : a.row};
    } else if (b.row > a.row) {
      link.exit = "bottom";
      link.enter = "top";
      link.first = {channel: a.row + 1};
      if (b.row > a.row + 1) {
        link.through = {channel: a.column < b.column ? b.column : b.column + 1};
        link.second = {channel: b.row};
      }
    } else {
      link.exit = "top";
      link.enter = "bottom";
      link.first = {channel: a.row};
      if (b.row < a.row - 1) {
        link.through = {channel: a.column > b.column ? b.column + 1 : b.column};
        link.second = {channel: b.row + 1};
      }
    }

    links.push(link);
  });

  const sides = new Map();
  const addEnd = (box, side, end) => {
    const key = box.group.id + ":" + side;
    if (!sides.has(key)) sides.set(key, {box: box, side: side, ends: []});
    sides.get(key).ends.push(end);
  }

  links.forEach((link) => {
    const via = link.through ? middle(layout.vertical[link.through.channel]) : null;
    addEnd(link.a, link.exit, {link: link, out: true, towards: via !== null ? via : link.b.x + link.b.width / 2});
    addEnd(link.b, link.enter, {link: link, out: false, towards: via !== null ? via : link.a.x + link.a.width / 2});
  });

  sides.forEach(({box, side, ends}) => {
    // Two boxes above each other exchange their links in two straight lines: what leaves on the right below
    // arrives on the right above.
    const outLast = side === "bottom" ? 1 : -1;
    ends.sort((p, q) => p.towards - q.towards
        || (p.out === q.out ? 0 : (p.out ? outLast : -outLast))
        || (p.link.id < q.link.id ? -1 : 1));

    const usable = box.width - 2 * PORT_MARGIN;
    ends.forEach((end, i) => {
      const x = box.x + PORT_MARGIN + usable * (i + 0.5) / ends.length;
      if (end.out) {
        end.link.ax = x;
      } else {
        end.link.bx = x;
      }
    });
  });

  const along = new Map();
  const across = new Map();
  const addStretch = (channels, channel, stretch, from, to) => {
    stretch.lo = Math.min(from, to);
    stretch.hi = Math.max(from, to);
    if (!channels.has(channel)) channels.set(channel, []);
    channels.get(channel).push(stretch);
  }

  links.forEach((link) => {
    if (link.through) {
      const via = middle(layout.vertical[link.through.channel]);
      addStretch(along, link.first.channel, link.first, link.ax, via);
      addStretch(along, link.second.channel, link.second, via, link.bx);
      addStretch(across, link.through.channel, link.through,
          middle(layout.horizontal[link.first.channel]), middle(layout.horizontal[link.second.channel]));
    } else {
      addStretch(along, link.first.channel, link.first, link.ax, link.bx);
    }
  });

  along.forEach((stretches, channel) => {
    const count = assignTracks(stretches);
    stretches.forEach((stretch) => {
      stretch.at = trackPosition(layout.horizontal[channel], stretch.track, count);
    });
  });
  across.forEach((stretches, channel) => {
    const count = assignTracks(stretches);
    stretches.forEach((stretch) => {
      stretch.at = trackPosition(layout.vertical[channel], stretch.track, count);
    });
  });

  links.forEach((link) => {
    const startY = link.exit === "bottom" ? link.a.y + link.a.height : link.a.y;
    const endY = link.enter === "top" ? link.b.y : link.b.y + link.b.height;
    const heading = link.enter === "top" ? 1 : -1;
    const lineEnd = endY - heading * (ARROW_LENGTH - 1);

    const points = [[link.ax, startY], [link.ax, link.first.at]];
    if (link.through) {
      points.push([link.through.at, link.first.at], [link.through.at, link.second.at], [link.bx, link.second.at]);
    } else {
      points.push([link.bx, link.first.at]);
    }
    points.push([link.bx, lineEnd]);

    link.path = chamferedPath(points);
    link.arrow = {x: link.bx, y: endY, heading: heading};
  });

  return links;
}

export const arrowPath = (arrow, width) => {
  const half = Math.max(2.6, 1.6 + width * 0.9);
  const base = arrow.y - arrow.heading * ARROW_LENGTH;
  return "M" + round(arrow.x) + "," + round(arrow.y)
      + "L" + round(arrow.x - half) + "," + round(base)
      + "L" + round(arrow.x + half) + "," + round(base) + "Z";
}
