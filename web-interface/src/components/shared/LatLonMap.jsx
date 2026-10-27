import React, {useEffect, useState} from 'react';

import 'leaflet/dist/leaflet.css';
import '../../../lib/Control.FullScreen';
import '../../../lib/Control.FullScreen.css';
import * as L from 'leaflet';
import {latLng} from "leaflet/src/geo";

export default function LatLonMap(props) {

  const containerHeight = props.containerHeight;
  const toggled = props.toggled; // Required to redraw on visibility toggle.
  const editMode = props.editMode;

  // Optional.
  const setLatitude = props.setLatitude;
  const setLongitude = props.setLongitude;
  const latitude = props.latitude;
  const longitude = props.longitude;
  const defaultZoomLevel = props.defaultZoomLevel;

  const icon = props.icon ? props.icon : L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/leaflet/icon-tap.png',
    iconSize: [40, 52],
    iconAnchor: [20, 52],
    tooltipAnchor: [0, -52]
  });

  const [map, setMap] = useState(null);
  const [zoomLevel, setZoomLevel] = useState(defaultZoomLevel ? defaultZoomLevel : 2);

  const removeMarker = () => {
    map.eachLayer(function (layer) {
      if (layer.options.nzymeType === "tap-marker") {
        layer.remove();
      }
    });
  }

  const placeMarker = (latlng) => {
    removeMarker();

    L.marker(latlng, {
      nzymeType: "tap-marker",
      icon: icon,
      draggable: false,
      autoPan: true
    }).addTo(map);

    if (editMode) {
      setLatitude(latlng.lat);
      setLongitude(latlng.lng);
    }
  }

  useEffect(() => {
    const newMap = L.map("tap-location-map", {
      scrollWheelZoom: true,
      fullscreenControl: true
    });

    newMap.attributionControl.setPrefix("");

    // Makes the bounds a hard edge.
    newMap.setMaxBounds([[-90, -180], [90, 180]]);
    newMap.options.maxBoundsViscosity = 1.0;

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(newMap);

    newMap.on('zoomend', () => setZoomLevel(newMap.getZoom()));

    setMap(newMap);

    return () => {
      newMap.remove();
    }
  }, []);

  useEffect(() => {
    if (map) {
      if (latitude && longitude) {
        map.setView(latLng(latitude, longitude), zoomLevel)
        placeMarker(latLng(latitude, longitude));
      } else {
        map.setView([0, 0], zoomLevel)
      }
    }
  }, [map, latitude, longitude])

  useEffect(() => {
    if (!map || !editMode) {
      return;
    }

    const onClick = (e) => placeMarker(e.latlng);
    map.on("click", onClick);

    return () => {
      map.off("click", onClick);
    }
  }, [map, editMode])

  useEffect(() => {
    if (map) {
      map.invalidateSize(false);
    }
  }, [toggled]);

  return (
      <React.Fragment>
        <div id="tap-location-map" style={{height: containerHeight}}/>

        { latitude && longitude && editMode ?
          <button href="" className="btn btn-sm btn-secondary float-end mt-1"
                  onClick={(e) => {e.preventDefault(); setLatitude(null); setLongitude(null); removeMarker()}}>
            Remove Location
          </button>
          : null }
      </React.Fragment>
  )

}