package app.nzyme.core.rest.resources.taps.reports.tables.dns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;

// Older taps still send an entropy_log field, which is ignored.
@JsonIgnoreProperties(ignoreUnknown = true)
@AutoValue
public abstract class DnsTablesReport {

    public abstract Map<String, DnsIpStatisticsReport> ips();
    public abstract List<DnsLogReport> queryLog();
    public abstract List<DnsLogReport> responseLog();

    @JsonCreator
    public static DnsTablesReport create(@JsonProperty("ips") Map<String, DnsIpStatisticsReport> ips,
                                         @JsonProperty("queries") List<DnsLogReport> queryLog,
                                         @JsonProperty("responses") List<DnsLogReport> responseLog) {
        return builder()
                .ips(ips)
                .queryLog(queryLog)
                .responseLog(responseLog)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DnsTablesReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ips(Map<String, DnsIpStatisticsReport> ips);

        public abstract Builder queryLog(List<DnsLogReport> queryLog);

        public abstract Builder responseLog(List<DnsLogReport> responseLog);

        public abstract DnsTablesReport build();
    }

}
