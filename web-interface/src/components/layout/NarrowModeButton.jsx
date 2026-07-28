import React from 'react'

export default function NarrowModeButton ({narrowModeEnabled, setNarrowModeEnabled}) {
    if (narrowModeEnabled) {
        return (
            <button className="btn btn-outline-secondary" onClick={() => { setNarrowModeEnabled(false) }} title="Disable Narrow Mode">
                <i className="fa-solid fa-maximize text-warning" />
            </button>
        )
    } else {
        return (
            <button className="btn btn-outline-secondary" onClick={() => { setNarrowModeEnabled(true) }} title="Enable Narrow Mode">
                <i className="fa-solid fa-minimize" />
            </button>
        )
    }
}