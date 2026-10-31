import React from "react";
import {Link} from "react-router-dom";
import {resolveBriefingLink} from "./briefingLinks";
import {formatValue, fullTime, relativeTime, severityMeta, subsystemName} from "./briefingFormat";
import BriefingCard from "./BriefingCard";

export default function ChangesSection({changes, window, type, hint}) {

  if (!changes || changes.length === 0) {
    return null;
  }

  return (
    <div className="row briefing-changes">
      {changes.map((group, i) => (
        <div key={group.key} className="col-12 mt-3">
          <BriefingCard title={(type === "MAIN" && group.subsystem ? subsystemName(group.subsystem) + ": " : "") + group.title}
                        internalLink={resolveBriefingLink(group.link, window)}
                        hint={[i === 0 ? hint : null, group.hint].filter(Boolean).join(" ")}>
            <TileLayout tiles={group.tiles} window={window} />
          </BriefingCard>
        </div>
      ))}
    </div>
  )

}

function TileLayout({tiles, window}) {

  const isRich = (tile) => tile.samples.length > 0 || (tile.breakdown && tile.breakdown.length > 0);
  const plain = tiles.filter((t) => !isRich(t));
  const rich = tiles.filter(isRich);

  return (
    <React.Fragment>
      {plain.length > 0 ? (
        <div className="briefing-tiles-strip">
          {plain.map((tile) => <ChangeTile key={tile.key} tile={tile} window={window} compact />)}
        </div>
      ) : null}
      {rich.length > 0 ? (
        <div className="briefing-tiles">
          {rich.map((tile) => <ChangeTile key={tile.key} tile={tile} window={window} />)}
        </div>
      ) : null}
    </React.Fragment>
  )

}

function ChangeTile({tile, window, compact}) {

  const href = resolveBriefingLink(tile.link, window);
  const hasPrevious = tile.previous !== null && tile.previous !== undefined;

  const delta = () => {
    if (!hasPrevious) {
      return null;
    }

    const diff = tile.current - tile.previous;
    let tone = "neutral";
    if (diff !== 0 && tile.higher_is_worse !== null && tile.higher_is_worse !== undefined) {
      const worse = tile.higher_is_worse ? diff > 0 : diff < 0;
      tone = worse ? "worse" : "better";
    }

    const glyph = diff > 0 ? "fa-solid fa-arrow-trend-up" : (diff < 0 ? "fa-solid fa-arrow-trend-down" : "fa-solid fa-minus");
    const text = diff === 0 ? "same as before" : (diff > 0 ? "+" : "") + formatValue(diff, tile.unit) + " vs " + formatValue(tile.previous, tile.unit) + " before";

    return <span className={"briefing-tile-delta " + tone}><i className={glyph} /> {text}</span>;
  }

  const hasBreakdown = tile.breakdown && tile.breakdown.length > 0;

  const number = <span className={"briefing-tile-value" + (tile.current === 0 ? " zero" : "")}>{formatValue(tile.current, tile.unit)}</span>;

  return (
    <div className={"briefing-tile" + (compact ? " briefing-tile-compact" : "")}>
      <div className="briefing-tile-head">
        {href ? <Link to={href} className="briefing-tile-link">{number}</Link> : number}
        <span className="briefing-tile-title">{href ? <Link to={href}>{tile.title}</Link> : tile.title}</span>
        {delta()}
      </div>

      {hasBreakdown ? <Breakdown entries={tile.breakdown} /> : null}

      {tile.samples.length > 0 ? (
        <ul className="briefing-samples">
          {tile.samples.map((sample, i) => {
            const sampleHref = resolveBriefingLink(sample.link, window);
            const meta = sample.severity ? severityMeta(sample.severity) : null;
            const content = (
              <React.Fragment>
                {meta ? <i className={meta.icon + " briefing-sample-sev sev-" + sample.severity.toLowerCase()} title={meta.label} /> : null}
                <span className="briefing-sample-label">{sample.label}</span>
                {sample.detail ? <span className="briefing-sample-detail">{sample.detail}</span> : null}
                {sample.timestamp ? <span className="briefing-sample-time" title={fullTime(sample.timestamp)}>{relativeTime(sample.timestamp)}</span> : null}
              </React.Fragment>
            );
            return (
              <li key={i}>{sampleHref ? <Link to={sampleHref}>{content}</Link> : content}</li>
            )
          })}
          {tile.current > tile.samples.length && href ? (
            <li className="briefing-samples-more"><Link to={href}>and {formatValue(tile.current - tile.samples.length, tile.unit)} more</Link></li>
          ) : null}
        </ul>
      ) : null}
    </div>
  )

}

function Breakdown({entries}) {

  const max = Math.max(...entries.map((e) => e.count));

  return (
    <ul className="briefing-breakdown">
      {entries.map((entry, i) => (
        <li key={i}>
          <span className="briefing-breakdown-label" title={entry.label}>{entry.label}</span>
          <span className="briefing-breakdown-bar"><i style={{width: Math.max(2, Math.round(entry.count / max * 100)) + "%"}} /></span>
          <span className="briefing-breakdown-count">{formatValue(entry.count, null)}</span>
        </li>
      ))}
    </ul>
  )

}
