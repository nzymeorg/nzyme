package app.nzyme.core.taps;

public record CaptureSummary(int total, int running, int dropping, long dropped) {

    public static final CaptureSummary NONE = new CaptureSummary(0, 0, 0, 0);

}
