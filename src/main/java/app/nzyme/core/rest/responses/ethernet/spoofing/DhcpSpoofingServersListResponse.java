package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class DhcpSpoofingServersListResponse {

    @JsonProperty("total")
    public abstract long total();

    @JsonProperty("servers")
    public abstract List<DhcpSpoofingServerResponse> servers();

    @Nullable
    @JsonProperty("learning_period_start")
    public abstract DateTime learningPeriodStart();

    @Nullable
    @JsonProperty("learning_period_end")
    public abstract DateTime learningPeriodEnd();

    @JsonProperty("learning_period_active")
    public abstract boolean learningPeriodActive();

    @JsonProperty("learning_period_ended_by_user")
    public abstract boolean learningPeriodEndedByUser();

    public static DhcpSpoofingServersListResponse create(long total, List<DhcpSpoofingServerResponse> servers, DateTime learningPeriodStart, DateTime learningPeriodEnd, boolean learningPeriodActive, boolean learningPeriodEndedByUser) {
        return builder()
                .total(total)
                .servers(servers)
                .learningPeriodStart(learningPeriodStart)
                .learningPeriodEnd(learningPeriodEnd)
                .learningPeriodActive(learningPeriodActive)
                .learningPeriodEndedByUser(learningPeriodEndedByUser)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DhcpSpoofingServersListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder total(long total);
        public abstract Builder servers(List<DhcpSpoofingServerResponse> servers);

        public abstract Builder learningPeriodStart(DateTime learningPeriodStart);

        public abstract Builder learningPeriodEnd(DateTime learningPeriodEnd);

        public abstract Builder learningPeriodActive(boolean learningPeriodActive);

        public abstract Builder learningPeriodEndedByUser(boolean learningPeriodEndedByUser);
        public abstract DhcpSpoofingServersListResponse build();
    }
}
