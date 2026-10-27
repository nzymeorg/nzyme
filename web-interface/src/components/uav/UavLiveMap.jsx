import React, {useEffect} from "react";

import 'leaflet/dist/leaflet.css';
import * as L from 'leaflet';
import '../../../lib/Control.FullScreen';
import '../../../lib/Control.FullScreen.css';
import '../../../lib/easy-button';
import '../../../lib/easy-button.css';
import moment from "moment/moment";
import GenericWidgetLoadingSpinner from "../widgets/GenericWidgetLoadingSpinner";
import {latLng} from "leaflet/src/geo";
import {sanitizeHtml} from "../../util/Tools";

export default function UavLiveMap(props) {

  const uavs = props.uavs;
  const containerHeight = props.containerHeight;

  // Optional.
  const onRefresh = props.onRefresh;
  const onUavClick = props.onUavClick;

  const uavIcon = L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/uav/uav.png',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    tooltipAnchor: [0, 0]
  });

  const uavTooltip = (uav) => {
    // Watch for XSS here.
    return "UAV [" + sanitizeHtml(uav.designation) + "]<br/>" + moment(uav.last_seen).fromNow();
  }

  useEffect(() => {
    if (uavs === null || uavs.uavs.length === 0) {
      return;
    }

    const map = L.map("uav-tactical-map", {
      scrollWheelZoom: false,
      fullscreenControl: true,
      fullscreenControlOptions: {
        position: "topleft"
      }
    });

    map.invalidateSize(true);
    map.attributionControl.setPrefix("");

    map.setView([uavs.map_center.latitude, uavs.map_center.longitude], uavs.map_center.zoom)

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);

    if (onRefresh) {
      L.easyButton({
        states: [{
          stateName: "refresh",
          icon: '<i class="fa-solid fa-refresh"></i>',
          title: "Refresh",
          onClick: onRefresh
        }]
      }).addTo(map);
    }

    // Paint each UAV on map.
    for (const uav of uavs.uavs) {
      const uavMarker = L.marker(latLng(uav.latitude, uav.longitude), {
        icon: uavIcon,
        draggable: false,
        autoPan: true
      }).addTo(map);

      uavMarker.bindTooltip(uavTooltip(uav));

      uavMarker.on('click', () => {
        onUavClick(uav);
      });
    }

    // Tears down the map and all of its handlers when the UAV set changes or the component unmounts.
    return () => {
      map.remove();
    }
  }, [uavs])

  if (uavs === null) {
    return <GenericWidgetLoadingSpinner height={containerHeight} />
  }

  if (uavs.uavs.length === 0) {
    return <div className="alert alert-info mb-0">No UAVs recorded in selected time frame.</div>
  }

  return (
      <React.Fragment>
        <div id="uav-tactical-map" style={{height: containerHeight}} />
      </React.Fragment>
  )

}