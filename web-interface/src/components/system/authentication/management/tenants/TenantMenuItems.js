import ApiRoutes from "../../../../../util/ApiRoutes";

export const TENANT_MENU_ITEMS = (organizationId, tenantId) => [
  {name: "Overview", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organizationId, tenantId) },
  {name: "Taps", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organizationId, tenantId) },
  {name: "Users", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organizationId, tenantId) },
  {name: "Locations", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organizationId, tenantId) },
  //{name: "Integrations", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.INTEGRATIONS_PAGE(organizationId, tenantId) },
  {name: "Database", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DATABASE_PAGE(organizationId, tenantId) },
  {name: "Quotas", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.QUOTAS_PAGE(organizationId, tenantId) },
  {name: "Subsystems", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.SUBSYSTEMS_PAGE(organizationId, tenantId) },
  {name: "Dashboards", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DASHBOARDS_PAGE(organizationId, tenantId) },
  {name: "Monitor Performance", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.MONITOR_PERFORMANCE_PAGE(organizationId, tenantId) }
]