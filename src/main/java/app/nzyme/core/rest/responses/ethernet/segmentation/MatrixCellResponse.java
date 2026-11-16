package app.nzyme.core.rest.responses.ethernet.segmentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;

@AutoValue
public abstract class MatrixCellResponse {

    @JsonProperty("source_group")
    public abstract long sourceGroup();

    @JsonProperty("destination_group")
    public abstract long destinationGroup();

    @Schema(description = "Sessions the destination answered.")
    @JsonProperty("connected")
    public abstract long connected();

    @Schema(description = "Sessions that got a reset or an ICMP destination unreachable back.")
    @JsonProperty("rejected")
    public abstract long rejected();

    @Schema(description = "Sessions that got nothing back.")
    @JsonProperty("no_reply")
    public abstract long noReply();

    @Schema(description = "Sessions that were sent to a broadcast or multicast address, where nothing is expected back.")
    @JsonProperty("sent")
    public abstract long sent();

    @Schema(description = "Of all sessions, the ones that were opened before the hour they are counted in.")
    @JsonProperty("continued")
    public abstract long continued();

    @Schema(description = "The services with the most sessions.")
    @JsonProperty("services")
    public abstract List<MatrixServiceResponse> services();

    @Schema(description = "Number of all services.")
    @JsonProperty("service_count")
    public abstract int serviceCount();

    @JsonProperty("tap_uuids")
    public abstract List<UUID> tapUuids();

    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();

    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    @Schema(description = "How much of the crossing policies have in scope: NONE, PARTIAL or FULL.")
    @JsonProperty("coverage")
    public abstract String coverage();

    @Schema(description = "The policies that have at least part of the crossing in scope.")
    @JsonProperty("policy_uuids")
    public abstract List<UUID> policyUuids();

    @JsonProperty("crossed_findings")
    public abstract long crossedFindings();

    @JsonProperty("attempted_findings")
    public abstract long attemptedFindings();

    public static MatrixCellResponse create(long sourceGroup,
                                            long destinationGroup,
                                            long connected,
                                            long rejected,
                                            long noReply,
                                            long sent,
                                            long continued,
                                            List<MatrixServiceResponse> services,
                                            int serviceCount,
                                            List<UUID> tapUuids,
                                            DateTime firstSeen,
                                            DateTime lastSeen,
                                            String coverage,
                                            List<UUID> policyUuids,
                                            long crossedFindings,
                                            long attemptedFindings) {
        return builder()
                .sourceGroup(sourceGroup)
                .destinationGroup(destinationGroup)
                .connected(connected)
                .rejected(rejected)
                .noReply(noReply)
                .sent(sent)
                .continued(continued)
                .services(services)
                .serviceCount(serviceCount)
                .tapUuids(tapUuids)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .coverage(coverage)
                .policyUuids(policyUuids)
                .crossedFindings(crossedFindings)
                .attemptedFindings(attemptedFindings)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MatrixCellResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder sourceGroup(long sourceGroup);
        public abstract Builder destinationGroup(long destinationGroup);
        public abstract Builder connected(long connected);
        public abstract Builder rejected(long rejected);
        public abstract Builder noReply(long noReply);
        public abstract Builder sent(long sent);
        public abstract Builder continued(long continued);
        public abstract Builder services(List<MatrixServiceResponse> services);
        public abstract Builder serviceCount(int serviceCount);
        public abstract Builder tapUuids(List<UUID> tapUuids);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);
        public abstract Builder coverage(String coverage);
        public abstract Builder policyUuids(List<UUID> policyUuids);
        public abstract Builder crossedFindings(long crossedFindings);
        public abstract Builder attemptedFindings(long attemptedFindings);

        public abstract MatrixCellResponse build();
    }

}
