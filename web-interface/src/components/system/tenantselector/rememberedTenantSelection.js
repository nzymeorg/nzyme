import Store from "../../../util/Store";

const KEY = "remembered_tenant_selections";
const MAX_USERS = 10;

const read = () => {
  const stored = Store.get(KEY);

  return stored && typeof stored === "object" && !Array.isArray(stored) ? stored : {};
}

export function rememberTenantSelection(user, organization, tenant) {
  if (!user || !user.id || !(user.is_superadmin || user.is_orgadmin) || !organization || !tenant) {
    return;
  }

  const selections = read();

  // Written last, so that the users who selected longest ago are the ones dropped.
  delete selections[user.id];
  selections[user.id] = {organization: organization, tenant: tenant};

  const users = Object.keys(selections);
  users.slice(0, Math.max(0, users.length - MAX_USERS)).forEach((id) => delete selections[id]);

  Store.set(KEY, selections);
}

export function rememberedTenantSelection(user) {
  const selection = user && user.id ? read()[user.id] : null;

  return selection && typeof selection.organization === "string" && typeof selection.tenant === "string"
    ? selection : null;
}

export function forgetTenantSelection(user) {
  if (!user || !user.id) {
    return;
  }

  const selections = read();
  delete selections[user.id];

  Store.set(KEY, selections);
}
