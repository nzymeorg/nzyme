import {useEffect, useState} from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import AssetTagsService from "../../../services/ethernet/AssetTagsService";

const assetTagsService = new AssetTagsService();

export const isAssetTagOperator = (operatorName) => {
  return typeof operatorName === "string" && operatorName.startsWith("asset_tag_");
}

export default function useAssetTagOptions(enabled) {

  const [organizationId, tenantId] = useSelectedTenant();
  const [options, setOptions] = useState(null);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    let current = true;
    assetTagsService.findAllTagsUnpaged(organizationId, tenantId, (tags) => {
      if (current) {
        setOptions(tags.map((tag) => ({value: tag.uuid, name: tag.name})));
      }
    });

    return () => {
      current = false;
    };
  }, [enabled, organizationId, tenantId]);

  return options;

}
