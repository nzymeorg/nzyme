import React from "react";
import createDOMPurify from 'dompurify'
import {marked} from "marked";

const DOMPurify = createDOMPurify(window);

function ContextNotes(props) {

  const notes = props.notes;

  const emptyText = props.emptyText ? props.emptyText : "This context has no notes attached to it.";

  if (!notes || !notes.trim()) {
    return <div className="alert alert-info mb-0">{emptyText}</div>
  }

  return <div className="rendered-markdown"
              dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(marked.parse(notes)) }} />;

}

export default ContextNotes;