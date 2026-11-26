package app.nzyme.core.dot11.disco;

public enum ConnectionFrameType {

    AUTHENTICATION,
    ASSOCIATION_REQUEST,
    ASSOCIATION_RESPONSE,
    REASSOCIATION_REQUEST,
    REASSOCIATION_RESPONSE,
    EAPOL_START,
    EAPOL_LOGOFF,
    EAP_REQUEST,
    EAP_RESPONSE,
    EAP_SUCCESS,
    EAP_FAILURE,
    HANDSHAKE_1,
    HANDSHAKE_2,
    HANDSHAKE_3,
    HANDSHAKE_4;

    public static boolean isKnown(String name) {
        try {
            valueOf(name);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

}
