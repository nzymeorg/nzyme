import useBackgroundRefresh from "./useBackgroundRefresh";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";

export default function SessionInfoRefresh({refresh}) {
  const [organizationId, tenantId] = useSelectedTenant();

  useBackgroundRefresh(refresh, [organizationId, tenantId]);

  return null;
}
