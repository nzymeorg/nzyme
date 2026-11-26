package app.nzyme.core.dot11.disco;

public enum DiscoAnomalyType {

    UNPROTECTED_FRAME("Unprotected Frame on Protected Network"),
    MIRRORED_FRAMES("Frames in Both Directions"),
    REPEATED_FRAMES("Repeated Transmissions"),
    SIGNAL_MISMATCH("Signal Strength Mismatch"),
    AUTHENTICATION_FLOOD("Authentication Flood"),
    EAPOL_FLOOD("EAPOL Flood"),
    EAP_FAILURE("Unexpected EAP Failure"),
    FRAME_OVERFLOW("Too Many Different Frames");

    private final String title;

    DiscoAnomalyType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

}
