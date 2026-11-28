/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.rest.resources.taps.reports;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class StatusReport {

    public abstract String version();
    public abstract DateTime timestamp();
    public abstract TotalWithAverage processedBytes();
    public abstract List<BusReport> buses();
    public abstract SystemMetrics systemMetrics();
    public abstract List<CapturesReport> captures();
    public abstract Map<String, Long> gaugesLong();
    public abstract Map<String, Double> gaugesFloat();
    public abstract Map<String, TimersReport> timers();
    public abstract Map<String, Long> logCounts();
    public abstract List<EngagementLogReport> engagementLogs();
    public abstract Map<String, Object> configuration();

    @Nullable
    public abstract Map<String, Object> portalIntegrityProbes();

    @Nullable
    public abstract String rpi();

    @JsonCreator
    public static StatusReport create(@JsonProperty("version") String version,
                                      @JsonProperty("timestamp") DateTime timestamp,
                                      @JsonProperty("processed_bytes") TotalWithAverage processedBytes,
                                      @JsonProperty("buses") List<BusReport> buses,
                                      @JsonProperty("system_metrics") SystemMetrics systemMetrics,
                                      @JsonProperty("captures") List<CapturesReport> captures,
                                      @JsonProperty("gauges_long") Map<String, Long> gaugesLong,
                                      @JsonProperty("gauges_float") Map<String, Double> gaugesFloat,
                                      @JsonProperty("timers") Map<String, TimersReport> timers,
                                      @JsonProperty("log_counts") Map<String, Long> logCounts,
                                      @JsonProperty("engagement_logs") List<EngagementLogReport> engagementLogs,
                                      @JsonProperty("configuration") Map<String, Object> configuration,
                                      @JsonProperty("portal_integrity_probes") Map<String, Object> portalIntegrityProbes,
                                      @JsonProperty("rpi") String rpi) {
        return builder()
                .version(version)
                .timestamp(timestamp)
                .processedBytes(processedBytes)
                .systemMetrics(systemMetrics)
                .buses(buses)
                .captures(captures)
                .gaugesLong(gaugesLong)
                .gaugesFloat(gaugesFloat)
                .timers(timers)
                .logCounts(logCounts)
                .configuration(configuration)
                .portalIntegrityProbes(portalIntegrityProbes)
                .engagementLogs(engagementLogs)
                .rpi(rpi)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_StatusReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder version(String version);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract Builder processedBytes(TotalWithAverage processedBytes);

        public abstract Builder buses(List<BusReport> buses);

        public abstract Builder systemMetrics(SystemMetrics systemMetrics);

        public abstract Builder captures(List<CapturesReport> captures);

        public abstract Builder gaugesLong(Map<String, Long> gaugesLong);

        public abstract Builder gaugesFloat(Map<String, Double> gaugesFloat);

        public abstract Builder timers(Map<String, TimersReport> timers);

        public abstract Builder logCounts(Map<String, Long> logCounts);

        public abstract Builder configuration(Map<String, Object> configuration);

        public abstract Builder portalIntegrityProbes(Map<String, Object> portalIntegrityProbes);

        public abstract Builder engagementLogs(List<EngagementLogReport> engagementLogs);

        public abstract Builder rpi(String rpi);

        public abstract StatusReport build();
    }

}
