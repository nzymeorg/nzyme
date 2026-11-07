package app.nzyme.core.rest.resources.taps.reports.tables.tcp;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@AutoValue
public abstract class TcpSynAckReport {

    public abstract int ipTtl();
    public abstract int ipTos();
    public abstract boolean ipDf();
    public abstract int ipId();
    public abstract boolean cwr();
    public abstract boolean ece();
    public abstract int windowSize();
    @Nullable
    public abstract Integer maximumSegmentSize();
    @Nullable
    public abstract Integer windowScaleMultiplier();
    public abstract List<Integer> options();
    @Nullable
    public abstract Long timestampValue();
    @Nullable
    public abstract Long timestampEcho();

    @JsonCreator
    public static TcpSynAckReport create(@JsonProperty("ip_ttl") int ipTtl,
                                         @JsonProperty("ip_tos") int ipTos,
                                         @JsonProperty("ip_df") boolean ipDf,
                                         @JsonProperty("ip_id") int ipId,
                                         @JsonProperty("cwr") boolean cwr,
                                         @JsonProperty("ece") boolean ece,
                                         @JsonProperty("window_size") int windowSize,
                                         @JsonProperty("maximum_segment_size") Integer maximumSegmentSize,
                                         @JsonProperty("window_scale_multiplier") Integer windowScaleMultiplier,
                                         @JsonProperty("options") List<Integer> options,
                                         @JsonProperty("timestamp_value") Long timestampValue,
                                         @JsonProperty("timestamp_echo") Long timestampEcho) {
        return builder()
                .ipTtl(ipTtl)
                .ipTos(ipTos)
                .ipDf(ipDf)
                .ipId(ipId)
                .cwr(cwr)
                .ece(ece)
                .windowSize(windowSize)
                .maximumSegmentSize(maximumSegmentSize)
                .windowScaleMultiplier(windowScaleMultiplier)
                .options(options == null ? List.of() : options)
                .timestampValue(timestampValue)
                .timestampEcho(timestampEcho)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TcpSynAckReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ipTtl(int ipTtl);
        public abstract Builder ipTos(int ipTos);
        public abstract Builder ipDf(boolean ipDf);
        public abstract Builder ipId(int ipId);
        public abstract Builder cwr(boolean cwr);
        public abstract Builder ece(boolean ece);
        public abstract Builder windowSize(int windowSize);
        public abstract Builder maximumSegmentSize(Integer maximumSegmentSize);
        public abstract Builder windowScaleMultiplier(Integer windowScaleMultiplier);
        public abstract Builder options(List<Integer> options);
        public abstract Builder timestampValue(Long timestampValue);
        public abstract Builder timestampEcho(Long timestampEcho);
        public abstract TcpSynAckReport build();
    }
}
