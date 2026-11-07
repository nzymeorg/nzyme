package app.nzyme.core.rest.resources.taps.reports.tables.tcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class TcpSessionReport {

    public abstract String state();
    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String destinationMac();
    public abstract String sourceAddress();
    public abstract int sourcePort();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract DateTime startTime();
    @Nullable
    public abstract DateTime endTime();
    public abstract DateTime mostRecentSegmentTime();
    public abstract long segmentsCount();
    public abstract long segmentsCountIncremental();
    public abstract long bytesCountRx();
    public abstract long bytesCountTx();
    public abstract long bytesCountRxIncremental();
    public abstract long bytesCountTxIncremental();
    public abstract int synIpTtl();
    public abstract int synIpTos();
    public abstract boolean synIpDf();
    public abstract boolean synCwr();
    public abstract boolean synEce();
    public abstract int synWindowSize();
    @Nullable
    public abstract Integer synMaximumSegmentSize();
    @Nullable
    public abstract Integer synMaximumScaleMultiplier();
    public abstract List<Integer> synOptions();

    // Older taps do not send these.
    @Nullable
    public abstract Integer synIpId();
    @Nullable
    public abstract Long synTimestampValue();
    @Nullable
    public abstract Long synTimestampEcho();
    @Nullable
    public abstract Long latestClientTimestampValue();
    @Nullable
    public abstract DateTime latestClientTimestampTime();
    @Nullable
    public abstract Integer latestClientIpId();
    @Nullable
    public abstract Long segmentsCountClientToServer();
    @Nullable
    public abstract TcpSynAckReport synAck();

    @Nullable
    public abstract List<String> tags();

    @JsonCreator
    public static TcpSessionReport create(@JsonProperty("state") String state,
                                          @JsonProperty("source_mac") String sourceMac,
                                          @JsonProperty("destination_mac") String destinationMac,
                                          @JsonProperty("source_address") String sourceAddress,
                                          @JsonProperty("source_port") int sourcePort,
                                          @JsonProperty("destination_address") String destinationAddress,
                                          @JsonProperty("destination_port") int destinationPort,
                                          @JsonProperty("start_time") DateTime startTime,
                                          @JsonProperty("end_time") DateTime endTime,
                                          @JsonProperty("most_recent_segment_time") DateTime mostRecentSegmentTime,
                                          @JsonProperty("segments_count") long segmentsCount,
                                          @JsonProperty("segments_count_incremental") long segmentsCountIncremental,
                                          @JsonProperty("bytes_count_rx") long bytesCountRx,
                                          @JsonProperty("bytes_count_tx") long bytesCountTx,
                                          @JsonProperty("bytes_count_rx_incremental") long bytesCountRxIncremental,
                                          @JsonProperty("bytes_count_tx_incremental") long bytesCountTxIncremental,
                                          @JsonProperty("syn_ip_ttl") int synIpTtl,
                                          @JsonProperty("syn_ip_tos") int synIpTos,
                                          @JsonProperty("syn_ip_df") boolean synIpDf,
                                          @JsonProperty("syn_cwr") boolean synCwr,
                                          @JsonProperty("syn_ece") boolean synEce,
                                          @JsonProperty("syn_window_size") int synWindowSize,
                                          @JsonProperty("syn_maximum_segment_size") Integer synMaximumSegmentSize,
                                          @JsonProperty("syn_window_scale_multiplier") Integer synMaximumScaleMultiplier,
                                          @JsonProperty("syn_options") List<Integer> synOptions,
                                          @JsonProperty("syn_ip_id") Integer synIpId,
                                          @JsonProperty("syn_timestamp_value") Long synTimestampValue,
                                          @JsonProperty("syn_timestamp_echo") Long synTimestampEcho,
                                          @JsonProperty("latest_client_timestamp_value") Long latestClientTimestampValue,
                                          @JsonProperty("latest_client_timestamp_time") DateTime latestClientTimestampTime,
                                          @JsonProperty("latest_client_ip_id") Integer latestClientIpId,
                                          @JsonProperty("segments_count_client_to_server") Long segmentsCountClientToServer,
                                          @JsonProperty("syn_ack") TcpSynAckReport synAck,
                                          @JsonProperty("tags") List<String> tags) {
        return builder()
                .state(state)
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .sourcePort(sourcePort)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .startTime(startTime)
                .endTime(endTime)
                .mostRecentSegmentTime(mostRecentSegmentTime)
                .segmentsCount(segmentsCount)
                .segmentsCountIncremental(segmentsCountIncremental)
                .bytesCountRx(bytesCountRx)
                .bytesCountTx(bytesCountTx)
                .bytesCountRxIncremental(bytesCountRxIncremental)
                .bytesCountTxIncremental(bytesCountTxIncremental)
                .synIpTtl(synIpTtl)
                .synIpTos(synIpTos)
                .synIpDf(synIpDf)
                .synCwr(synCwr)
                .synEce(synEce)
                .synWindowSize(synWindowSize)
                .synMaximumSegmentSize(synMaximumSegmentSize)
                .synMaximumScaleMultiplier(synMaximumScaleMultiplier)
                .synOptions(synOptions)
                .synIpId(synIpId)
                .synTimestampValue(synTimestampValue)
                .synTimestampEcho(synTimestampEcho)
                .latestClientTimestampValue(latestClientTimestampValue)
                .latestClientTimestampTime(latestClientTimestampTime)
                .latestClientIpId(latestClientIpId)
                .segmentsCountClientToServer(segmentsCountClientToServer)
                .synAck(synAck)
                .tags(tags)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TcpSessionReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder state(String state);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder sourcePort(int sourcePort);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationPort(int destinationPort);

        public abstract Builder startTime(DateTime startTime);

        public abstract Builder endTime(DateTime endTime);

        public abstract Builder mostRecentSegmentTime(DateTime mostRecentSegmentTime);

        public abstract Builder segmentsCount(long segmentCount);

        public abstract Builder segmentsCountIncremental(long segmentCountIncremental);

        public abstract Builder bytesCountRx(long bytesCountRx);

        public abstract Builder bytesCountTx(long bytesCountTx);

        public abstract Builder bytesCountRxIncremental(long bytesCountRxIncremental);

        public abstract Builder bytesCountTxIncremental(long bytesCountTxIncremental);

        public abstract Builder synIpTtl(int synIpTtl);

        public abstract Builder synIpTos(int synIpTos);

        public abstract Builder synIpDf(boolean synIpDf);

        public abstract Builder synCwr(boolean synCwr);

        public abstract Builder synEce(boolean synEce);

        public abstract Builder synWindowSize(int synWindowSize);

        public abstract Builder synMaximumSegmentSize(Integer synMaximumSegmentSize);

        public abstract Builder synMaximumScaleMultiplier(Integer synMaximumScaleMultiplier);

        public abstract Builder synOptions(List<Integer> synOptions);
        public abstract Builder synIpId(Integer synIpId);
        public abstract Builder synTimestampValue(Long synTimestampValue);
        public abstract Builder synTimestampEcho(Long synTimestampEcho);
        public abstract Builder latestClientTimestampValue(Long latestClientTimestampValue);
        public abstract Builder latestClientTimestampTime(DateTime latestClientTimestampTime);
        public abstract Builder latestClientIpId(Integer latestClientIpId);
        public abstract Builder segmentsCountClientToServer(Long segmentsCountClientToServer);
        public abstract Builder synAck(TcpSynAckReport synAck);

        public abstract Builder tags(List<String> tags);

        public abstract TcpSessionReport build();
    }

}
