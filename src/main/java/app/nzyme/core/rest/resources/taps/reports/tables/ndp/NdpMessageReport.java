package app.nzyme.core.rest.resources.taps.reports.tables.ndp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class NdpMessageReport {

    public abstract String messageType();

    @Nullable
    public abstract String sourceMac();

    @Nullable
    public abstract String destinationMac();

    public abstract String sourceAddress();

    public abstract String destinationAddress();

    public abstract int hopLimit();

    @Nullable
    public abstract String targetAddress();

    @Nullable
    public abstract String sourceLinkLayerAddress();

    @Nullable
    public abstract String targetLinkLayerAddress();

    @Nullable
    public abstract Boolean router();

    @Nullable
    public abstract Boolean solicited();

    @Nullable
    public abstract Boolean overrides();

    public abstract long packetCount();

    public abstract DateTime firstSeen();

    public abstract DateTime lastSeen();

    @JsonCreator
    public static NdpMessageReport create(@JsonProperty("message_type") String messageType,
                                          @JsonProperty("source_mac") String sourceMac,
                                          @JsonProperty("destination_mac") String destinationMac,
                                          @JsonProperty("source_address") String sourceAddress,
                                          @JsonProperty("destination_address") String destinationAddress,
                                          @JsonProperty("hop_limit") int hopLimit,
                                          @JsonProperty("target_address") String targetAddress,
                                          @JsonProperty("source_link_layer_address") String sourceLinkLayerAddress,
                                          @JsonProperty("target_link_layer_address") String targetLinkLayerAddress,
                                          @JsonProperty("router") Boolean router,
                                          @JsonProperty("solicited") Boolean solicited,
                                          @JsonProperty("overrides") Boolean overrides,
                                          @JsonProperty("packet_count") long packetCount,
                                          @JsonProperty("first_seen") DateTime firstSeen,
                                          @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .messageType(messageType)
                .sourceMac(sourceMac)
                .destinationMac(destinationMac)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .hopLimit(hopLimit)
                .targetAddress(targetAddress)
                .sourceLinkLayerAddress(sourceLinkLayerAddress)
                .targetLinkLayerAddress(targetLinkLayerAddress)
                .router(router)
                .solicited(solicited)
                .overrides(overrides)
                .packetCount(packetCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpMessageReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder messageType(String messageType);

        public abstract Builder sourceMac(String sourceMac);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder sourceAddress(String sourceAddress);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder hopLimit(int hopLimit);

        public abstract Builder targetAddress(String targetAddress);

        public abstract Builder sourceLinkLayerAddress(String sourceLinkLayerAddress);

        public abstract Builder targetLinkLayerAddress(String targetLinkLayerAddress);

        public abstract Builder router(Boolean router);

        public abstract Builder solicited(Boolean solicited);

        public abstract Builder overrides(Boolean overrides);

        public abstract Builder packetCount(long packetCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract NdpMessageReport build();
    }

}
