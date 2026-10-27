package app.nzyme.core.rest.resources.taps.reports.tables.nameres;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.List;

@AutoValue
public abstract class NameResolutionResponseReport {

    public abstract String protocol();
    public abstract String responderMac();
    public abstract String responderAddress();
    public abstract String name();
    public abstract List<String> answerAddresses();
    public abstract long responseCount();
    public abstract long unicastCount();
    public abstract List<NameResolutionVictimReport> victims();
    public abstract DateTime firstSeen();
    public abstract DateTime lastSeen();

    @JsonCreator
    public static NameResolutionResponseReport create(@JsonProperty("protocol") String protocol,
                                                      @JsonProperty("responder_mac") String responderMac,
                                                      @JsonProperty("responder_address") String responderAddress,
                                                      @JsonProperty("name") String name,
                                                      @JsonProperty("answer_addresses") List<String> answerAddresses,
                                                      @JsonProperty("response_count") long responseCount,
                                                      @JsonProperty("unicast_count") long unicastCount,
                                                      @JsonProperty("victims") List<NameResolutionVictimReport> victims,
                                                      @JsonProperty("first_seen") DateTime firstSeen,
                                                      @JsonProperty("last_seen") DateTime lastSeen) {
        return builder()
                .protocol(protocol)
                .responderMac(responderMac)
                .responderAddress(responderAddress)
                .name(name)
                .answerAddresses(answerAddresses)
                .responseCount(responseCount)
                .unicastCount(unicastCount)
                .victims(victims)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NameResolutionResponseReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder protocol(String protocol);

        public abstract Builder responderMac(String responderMac);

        public abstract Builder responderAddress(String responderAddress);

        public abstract Builder name(String name);

        public abstract Builder answerAddresses(List<String> answerAddresses);

        public abstract Builder responseCount(long responseCount);

        public abstract Builder unicastCount(long unicastCount);

        public abstract Builder victims(List<NameResolutionVictimReport> victims);

        public abstract Builder firstSeen(DateTime firstSeen);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract NameResolutionResponseReport build();
    }
}
