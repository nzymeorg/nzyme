package app.nzyme.core.ethernet.segmentation;

public record SegmentationConfiguration(boolean enabled,
                                        int settleSeconds,
                                        int initialBackfillHours,
                                        int flowRetentionTimeDays,
                                        int findingRetentionTimeDays) { }
