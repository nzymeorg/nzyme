import React from 'react'
import AssetStylesheet from '../misc/AssetStylesheet'

export default function DarkMode({enabled}) {
  if (enabled) {
    return <AssetStylesheet filename="dark.css" />
  } else {
    return null
  }
}