package app.nzyme.core.rest.resources.taps.reports.tables.tcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

/**
 * A stateless TCP segment the tap could not attribute to a session and that no real stack sends: NULL, SYN/FIN,
 * FIN or URG without ACK, or a non-SYN segment carrying SYN-only options. OS fingerprinting and stealth port
 * scans look like this on the wire.
 */
@AutoValue
public abstract class TcpProbeReport {

    @Nullable
    public abstract String sourceMac();
    @Nullable
    public abstract String destinationMac();
    public abstract String sourceAddress();
    public abstract String destinationAddress();
    public abstract int destinationPort();
    public abstract boolean flagSyn();
    public abstract boolean flagAck();
    public abstract boolean flagFin();
    public abstract boolean flagRst();
    public abstract boolean flagPsh();
    public abstract boolean flagUrg();
    public abstract boolean flagEce();
    public abstract boolean flagCwr();
    public abstract int windowSize();
    @Nullable
    public abstract Integer maximumSegmentSize();
    @Nullable
    public abstract Integer windowScaleMultiplier();
    public abstract List<Integer> options();
    public abstract int ipTtl();
    public abstract int ipTos();
    public abstract boolean ipDf();
    public abstract long packetCount();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    @JsonCreator
    public static TcpProbeReport create(@JsonProperty("source_mac") String sourceMac,
                                        @JsonProperty("destination_mac") String destinationMac,
                                        @JsonProperty("source_address") String sourceAddress,
                                        @JsonProperty("destination_address") String destinationAddress,
                                        @JsonProperty("destination_port") int destinationPort,
                                        @JsonProperty("flag_syn") boolean flagSyn,
                                        @JsonProperty("flag_ack") boolean flagAck,
                                        @JsonProperty("flag_fin") boolean flagFin,
                                        @JsonProperty("flag_rst") boolean flagRst,
                                        @JsonProperty("flag_psh") boolean flagPsh,
                                        @JsonProperty("flag_urg") boolean flagUrg,
                                        @JsonProperty("flag_ece") boolean flagEce,
                                        @JsonProperty("flag_cwr") boolean flagCwr,
                                        @JsonProperty("window_size") int windowSize,
                                        @JsonProperty("maximum_segment_size") Integer maximumSegmentSize,
                                        @JsonProperty("window_scale_multiplier") Integer windowScaleMultiplier,
                                        @JsonProperty("options") List<Integer> options,
                                        @JsonProperty("ip_ttl") int ipTtl,
                                        @JsonProperty("ip_tos") int ipTos,
                                        @JsonProperty("ip_df") boolean ipDf,
                                        @JsonProperty("packet_count") long packetCount,
                                        @JsonProperty("first_seen") DateTime firstSeen,
                                        @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .destinationPort(destinationPort)
                .flagSyn(flagSyn)
                .flagAck(flagAck)
                .flagFin(flagFin)
                .flagRst(flagRst)
                .flagPsh(flagPsh)
                .flagUrg(flagUrg)
                .flagEce(flagEce)
                .flagCwr(flagCwr)
                .windowSize(windowSize)
                .maximumSegmentSize(maximumSegmentSize)
                .windowScaleMultiplier(windowScaleMultiplier)
                .options(options)
                .ipTtl(ipTtl)
                .ipTos(ipTos)
                .ipDf(ipDf)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_TcpProbeReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceMac(String sourceMac);
        public abstract Builder destinationMac(String destinationMac);
        public abstract Builder sourceAddress(String sourceAddress);
        public abstract Builder destinationAddress(String destinationAddress);
        public abstract Builder destinationPort(int destinationPort);
        public abstract Builder flagSyn(boolean flagSyn);
        public abstract Builder flagAck(boolean flagAck);
        public abstract Builder flagFin(boolean flagFin);
        public abstract Builder flagRst(boolean flagRst);
        public abstract Builder flagPsh(boolean flagPsh);
        public abstract Builder flagUrg(boolean flagUrg);
        public abstract Builder flagEce(boolean flagEce);
        public abstract Builder flagCwr(boolean flagCwr);
        public abstract Builder windowSize(int windowSize);
        public abstract Builder maximumSegmentSize(Integer maximumSegmentSize);
        public abstract Builder windowScaleMultiplier(Integer windowScaleMultiplier);
        public abstract Builder options(List<Integer> options);
        public abstract Builder ipTtl(int ipTtl);
        public abstract Builder ipTos(int ipTos);
        public abstract Builder ipDf(boolean ipDf);
        public abstract Builder packetCount(long packetCount);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract TcpProbeReport build();
    }
}
