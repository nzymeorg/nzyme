package app.nzyme.core.rest.responses.ethernet.ndp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class NdpMessagesListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("messages")
    public abstract List<NdpMessageResponse> messages();

    public static NdpMessagesListResponse create(long total,
                                                 List<NdpMessageResponse> messages) {
        return builder()
                .total(total)
                .messages(messages)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NdpMessagesListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);

        public abstract Builder messages(List<NdpMessageResponse> messages);

        public abstract NdpMessagesListResponse build();
    }

}
