package app.nzyme.core.rest.responses.ethernet.ndp;

import app.nzyme.core.rest.responses.ethernet.InternalAddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class NdpMessageResponse {

    @JsonProperty("tap_uuid")
    public abstract UUID tapUuid();

    @JsonProperty("message_type")
    public abstract String messageType();

    @JsonProperty("source")
    public abstract InternalAddressResponse source();

    @JsonProperty("destination_address")
    public abstract String destinationAddress();

    @JsonProperty("destination_mac")
    @Nullable
    public abstract String destinationMac();

    @JsonProperty("hop_limit")
    public abstract int hopLimit();

    @JsonProperty("target")
    @Nullable
    public abstract InternalAddressResponse target();

    @JsonProperty("source_link_layer_address")
    @Nullable
    public abstract String sourceLinkLayerAddress();

    @JsonProperty("target_link_layer_address")
    @Nullable
    public abstract String targetLinkLayerAddress();

    @JsonProperty("router")
    @Nullable
    public abstract Boolean router();

    @JsonProperty("solicited")
    @Nullable
    public abstract Boolean solicited();

    @JsonProperty("overrides")
    @Nullable
    public abstract Boolean overrides();

    @JsonProperty("packet_count")
    public abstract long packetCount();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static NdpMessageResponse create(UUID tapUuid,
                                            String messageType,
                                            InternalAddressResponse source,
                                            String destinationAddress,
                                            String destinationMac,
                                            int hopLimit,
                                            InternalAddressResponse target,
                                            String sourceLinkLayerAddress,
                                            String targetLinkLayerAddress,
                                            Boolean router,
                                            Boolean solicited,
                                            Boolean overrides,
                                            long packetCount,
                                            DateTime firstSeen,
                                            DateTime lastSeen) {
        return builder()
                .tapUuid(tapUuid)
                .messageType(messageType)
                .source(source)
                .destinationAddress(destinationAddress)
                .destinationMac(destinationMac)
                .hopLimit(hopLimit)
                .target(target)
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
        return new AutoValue_NdpMessageResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder messageType(String messageType);

        public abstract Builder source(InternalAddressResponse source);

        public abstract Builder destinationAddress(String destinationAddress);

        public abstract Builder destinationMac(String destinationMac);

        public abstract Builder hopLimit(int hopLimit);

        public abstract Builder target(InternalAddressResponse target);

        public abstract Builder sourceLinkLayerAddress(String sourceLinkLayerAddress);

        public abstract Builder targetLinkLayerAddress(String targetLinkLayerAddress);

        public abstract Builder router(Boolean router);

        public abstract Builder solicited(Boolean solicited);

        public abstract Builder overrides(Boolean overrides);

        public abstract Builder packetCount(long packetCount);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract NdpMessageResponse build();
    }

}
