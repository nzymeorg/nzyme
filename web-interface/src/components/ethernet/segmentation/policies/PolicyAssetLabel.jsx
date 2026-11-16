import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import AssetsService from "../../../../services/ethernet/AssetsService";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";

const assetsService = new AssetsService();

const names = {};
const waiting = {};

const nameOf = (asset) => {
  if (asset.name) {
    return asset.name;
  }

  return asset.mac && asset.mac.address ? asset.mac.address : null;
}

export const rememberAssetName = (asset) => {
  const name = nameOf(asset);
  if (asset.uuid && name) {
    names[asset.uuid] = name;
  }
}

const lookUp = (uuid, organizationId, tenantId, callback) => {
  if (waiting[uuid]) {
    waiting[uuid].push(callback);
    return;
  }

  waiting[uuid] = [callback];
  assetsService.findAsset(uuid, organizationId, tenantId, (asset) => {
    rememberAssetName(asset);

    const callbacks = waiting[uuid];
    delete waiting[uuid];
    callbacks.forEach((c) => c(nameOf(asset)));
  });
}

export default function PolicyAssetLabel({uuid, link = true}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [name, setName] = useState(names[uuid] ? names[uuid] : null);

  useEffect(() => {
    if (names[uuid]) {
      setName(names[uuid]);
      return;
    }

    let current = true;

    setName(null);
    lookUp(uuid, organizationId, tenantId, (found) => {
      if (current) {
        setName(found);
      }
    });

    return () => { current = false; };
  }, [uuid, organizationId, tenantId]);

  const label = name ? name : uuid.slice(0, 6);

  return (
    <span className="text-nowrap" title={"Asset " + uuid}>
      Asset{' '}
      {link ? <Link to={ApiRoutes.ETHERNET.ASSETS.DETAILS(uuid)}>{label}</Link> : label}
    </span>
  )

}
