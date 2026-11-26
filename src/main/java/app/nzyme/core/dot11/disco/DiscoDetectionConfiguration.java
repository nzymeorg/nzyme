package app.nzyme.core.dot11.disco;

public record DiscoDetectionConfiguration(boolean monitoredNetworksOnly,
                                          boolean unprotectedFrameEnabled,
                                          boolean unprotectedFrameIgnoreMissingState,
                                          boolean mirroredFramesEnabled,
                                          boolean repeatedFramesEnabled,
                                          int repeatedFramesMinimumSequenceNumbers,
                                          int repeatedFramesMinimumFrames,
                                          boolean signalMismatchEnabled,
                                          int signalMismatchMinimumDifference,
                                          boolean authenticationFloodEnabled,
                                          int authenticationFloodMinimumTransmitters,
                                          boolean eapolFloodEnabled,
                                          int eapolFloodMinimumFrames,
                                          boolean eapFailureEnabled,
                                          int eapFailureMinimumFailures,
                                          boolean frameOverflowEnabled) {

    public boolean anyEnabled() {
        return unprotectedFrameEnabled || mirroredFramesEnabled || repeatedFramesEnabled || signalMismatchEnabled
                || authenticationFloodEnabled || eapolFloodEnabled || eapFailureEnabled || frameOverflowEnabled;
    }

}
