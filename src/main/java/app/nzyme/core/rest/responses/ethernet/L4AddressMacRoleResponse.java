package app.nzyme.core.rest.responses.ethernet;

public enum L4AddressMacRoleResponse {

    DEVICE, UNCLAIMED, PUBLIC, UNKNOWN;

    public boolean withoutAsset() {
        return this == UNCLAIMED || this == PUBLIC;
    }

}
