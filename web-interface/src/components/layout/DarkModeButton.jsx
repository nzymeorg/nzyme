import React from 'react'
import Store from '../../util/Store'

export default function DarkModeButton ({darkModeEnabled, setDarkModeEnabled}) {
  if (darkModeEnabled) {
    return (
            <button className="btn btn-outline-secondary" onClick={() => { setDarkModeEnabled(false) }} title="Enable Light Mode">
                <i className="fa-solid fa-sun" />
            </button>
    )
  } else {
    return (
            <button className="btn btn-outline-secondary" onClick={() => { setDarkModeEnabled(true) }} title="Enable Dark Mode">
                <i className="fa-solid fa-moon" />
            </button>
    )
  }
}