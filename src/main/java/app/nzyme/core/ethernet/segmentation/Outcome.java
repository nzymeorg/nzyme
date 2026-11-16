package app.nzyme.core.ethernet.segmentation;

public enum Outcome {

    CONNECTED,
    REJECTED,
    NO_REPLY,
    // Sent to a broadcast or multicast address. Whoever is in reach of it has received what was sent.
    SENT;

    public FindingClass findingClass() {
        return this == CONNECTED || this == SENT ? FindingClass.CROSSED : FindingClass.ATTEMPTED;
    }

}
