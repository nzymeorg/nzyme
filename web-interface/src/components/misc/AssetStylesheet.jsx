import React from 'react'

function AssetStylesheet (props) {
  return <link rel="stylesheet" href={window.appConfig.assetsUri + 'static/css/' + props.filename} />
}

export default AssetStylesheet
