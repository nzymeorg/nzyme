package app.nzyme.core.rest.responses.dot11.channelswitches;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class ChannelSwitchListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("announcements")
    public abstract List<ChannelSwitchDetailsResponse> announcements();

    public static ChannelSwitchListResponse create(long total, List<ChannelSwitchDetailsResponse> announcements) {
        return builder()
                .total(total)
                .announcements(announcements)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ChannelSwitchListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder announcements(List<ChannelSwitchDetailsResponse> announcements);

        public abstract ChannelSwitchListResponse build();
    }

}
