import React from "react";
import {MINIMUM_QUERY_LENGTH, normalizedQuery} from "./searchQuery";

export function needlesOf(query, interpretations) {
  const needles = [];
  const add = (value) => {
    const needle = normalizedQuery(value).toLowerCase();
    if (needle.length >= MINIMUM_QUERY_LENGTH && !needles.includes(needle)) {
      needles.push(needle);
    }
  }

  add(query);
  (interpretations || []).forEach((interpretation) => add(interpretation.value));

  return needles;
}

const escape = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export default function SearchHighlight({text, needles}) {
  if (text === null || text === undefined) {
    return null;
  }

  const value = String(text);
  if (!needles || needles.length === 0) {
    return value;
  }

  // Matched in the text as it is. Positions found in a lower-cased copy can be off: some letters change length.
  const pattern = new RegExp(needles.slice().sort((a, b) => b.length - a.length).map(escape).join("|"), "gi");

  const parts = [];
  let position = 0;
  let match = pattern.exec(value);
  while (match !== null && match[0].length > 0) {
    if (match.index > position) {
      parts.push(value.substring(position, match.index));
    }
    parts.push(<mark key={match.index} className="search-mark">{match[0]}</mark>);
    position = match.index + match[0].length;
    match = pattern.exec(value);
  }

  if (parts.length === 0) {
    return value;
  }

  if (position < value.length) {
    parts.push(value.substring(position));
  }

  return <React.Fragment>{parts}</React.Fragment>;
}
