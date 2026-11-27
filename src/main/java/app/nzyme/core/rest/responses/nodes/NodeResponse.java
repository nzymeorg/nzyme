package app.nzyme.core.rest.responses.nodes;

import app.nzyme.core.distributed.NodeHeap;
import app.nzyme.core.rest.responses.metrics.GaugeResponse;
import app.nzyme.core.rest.responses.metrics.TimerResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.Map;

@AutoValue
public abstract class NodeResponse {

    @JsonProperty("uuid")
    public abstract String uuid();

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("active")
    public abstract Boolean active();

    @JsonProperty("http_listen_uri")
    public abstract String httpListenUri();

    @JsonProperty("http_external_uri")
    public abstract String httpExternalUri();

    @JsonProperty("tls_cert_fingerprint")
    public abstract String tlsCertFingerprint();

    @JsonProperty("tls_cert_expiration_date")
    public abstract DateTime tlsCertExpirationDate();

    @JsonProperty("memory_bytes_total")
    public abstract long memoryBytesTotal();

    @JsonProperty("memory_bytes_available")
    public abstract long memoryBytesAvailable();

    @JsonProperty("memory_bytes_used")
    public abstract long memoryBytesUsed();

    @JsonProperty("heap_bytes_total")
    public abstract long heapBytesTotal();

    @JsonProperty("heap_bytes_available")
    public abstract long heapBytesAvailable();

    @JsonProperty("heap_bytes_used")
    public abstract long heapBytesUsed();

    @JsonProperty("heap_status")
    public abstract NodeHeap.Status heapStatus();

    @Nullable
    @JsonProperty("gc_pause_time_percent")
    public abstract Double gcPauseTimePercent();

    @Nullable
    @JsonProperty("heap_after_gc_percent")
    public abstract Double heapAfterGcPercent();

    @Nullable
    @JsonProperty("memory_pressure_percent")
    public abstract Double memoryPressurePercent();

    @JsonProperty("cpu_system_load")
    public abstract double cpuSystemLoad();

    @JsonProperty("cpu_thread_count")
    public abstract int cpuThreadCount();

    @JsonProperty("process_start_time")
    public abstract DateTime processStartTime();

    @JsonProperty("process_virtual_size")
    public abstract long processVirtualSize();

    @JsonProperty("process_arguments")
    public abstract String processArguments();

    @JsonProperty("os_information")
    public abstract String osInformation();

    @JsonProperty("version")
    public abstract String version();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @JsonProperty("deleted")
    public abstract boolean deleted();

    @JsonProperty("clock")
    public abstract DateTime clock();

    @JsonProperty("clock_drift_ms")
    public abstract long clockDriftMs();

    @JsonProperty("is_ephemeral")
    public abstract boolean isEphemeral();

    @JsonProperty("cycle")
    public abstract long cycle();

    @JsonProperty("metrics_timers")
    public abstract Map<String, TimerResponse> metricsTimers();

    @JsonProperty("metrics_gauges")
    public abstract Map<String, GaugeResponse> metricsGauges();

    public static NodeResponse create(String uuid, String name, Boolean active, String httpListenUri, String httpExternalUri, String tlsCertFingerprint, DateTime tlsCertExpirationDate, long memoryBytesTotal, long memoryBytesAvailable, long memoryBytesUsed, long heapBytesTotal, long heapBytesAvailable, long heapBytesUsed, NodeHeap.Status heapStatus, Double gcPauseTimePercent, Double heapAfterGcPercent, Double memoryPressurePercent, double cpuSystemLoad, int cpuThreadCount, DateTime processStartTime, long processVirtualSize, String processArguments, String osInformation, String version, DateTime lastSeen, boolean deleted, DateTime clock, long clockDriftMs, boolean isEphemeral, long cycle, Map<String, TimerResponse> metricsTimers, Map<String, GaugeResponse> metricsGauges) {
        return builder()
                .uuid(uuid)
                .name(name)
                .active(active)
                .httpListenUri(httpListenUri)
                .httpExternalUri(httpExternalUri)
                .tlsCertFingerprint(tlsCertFingerprint)
                .tlsCertExpirationDate(tlsCertExpirationDate)
                .memoryBytesTotal(memoryBytesTotal)
                .memoryBytesAvailable(memoryBytesAvailable)
                .memoryBytesUsed(memoryBytesUsed)
                .heapBytesTotal(heapBytesTotal)
                .heapBytesAvailable(heapBytesAvailable)
                .heapBytesUsed(heapBytesUsed)
                .heapStatus(heapStatus)
                .gcPauseTimePercent(gcPauseTimePercent)
                .heapAfterGcPercent(heapAfterGcPercent)
                .memoryPressurePercent(memoryPressurePercent)
                .cpuSystemLoad(cpuSystemLoad)
                .cpuThreadCount(cpuThreadCount)
                .processStartTime(processStartTime)
                .processVirtualSize(processVirtualSize)
                .processArguments(processArguments)
                .osInformation(osInformation)
                .version(version)
                .lastSeen(lastSeen)
                .deleted(deleted)
                .clock(clock)
                .clockDriftMs(clockDriftMs)
                .isEphemeral(isEphemeral)
                .cycle(cycle)
                .metricsTimers(metricsTimers)
                .metricsGauges(metricsGauges)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NodeResponse.Builder();
    }
    
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(String uuid);

        public abstract Builder name(String name);

        public abstract Builder active(Boolean active);

        public abstract Builder httpListenUri(String httpListenUri);

        public abstract Builder httpExternalUri(String httpExternalUri);

        public abstract Builder tlsCertFingerprint(String tlsCertFingerprint);

        public abstract Builder tlsCertExpirationDate(DateTime tlsCertExpirationDate);

        public abstract Builder memoryBytesTotal(long memoryBytesTotal);

        public abstract Builder memoryBytesAvailable(long memoryBytesAvailable);

        public abstract Builder memoryBytesUsed(long memoryBytesUsed);

        public abstract Builder heapBytesTotal(long heapBytesTotal);

        public abstract Builder heapBytesAvailable(long heapBytesAvailable);

        public abstract Builder heapBytesUsed(long heapBytesUsed);

        public abstract Builder heapStatus(NodeHeap.Status heapStatus);

        public abstract Builder gcPauseTimePercent(Double gcPauseTimePercent);

        public abstract Builder heapAfterGcPercent(Double heapAfterGcPercent);

        public abstract Builder memoryPressurePercent(Double memoryPressurePercent);

        public abstract Builder cpuSystemLoad(double cpuSystemLoad);

        public abstract Builder cpuThreadCount(int cpuThreadCount);

        public abstract Builder processStartTime(DateTime processStartTime);

        public abstract Builder processVirtualSize(long processVirtualSize);

        public abstract Builder processArguments(String processArguments);

        public abstract Builder osInformation(String osInformation);

        public abstract Builder version(String version);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract Builder deleted(boolean deleted);

        public abstract Builder clock(DateTime clock);

        public abstract Builder clockDriftMs(long clockDriftMs);

        public abstract Builder isEphemeral(boolean isEphemeral);

        public abstract Builder cycle(long cycle);

        public abstract Builder metricsTimers(Map<String, TimerResponse> metricsTimers);

        public abstract Builder metricsGauges(Map<String, GaugeResponse> metricsGauges);

        public abstract NodeResponse build();
    }
}
