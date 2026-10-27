package app.nzyme.core.ethernet.dns;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.database.generic.DateTimeNumberAggregationResult;
import app.nzyme.core.ethernet.Ethernet;
import app.nzyme.core.ethernet.dns.db.*;
import app.nzyme.core.ethernet.dns.filters.DnsFilters;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.filters.FilterSqlFragment;
import app.nzyme.core.util.filters.FilterSql;
import app.nzyme.core.util.filters.Filters;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTime;

import java.util.*;

public class DNS {

    private final NzymeNode nzyme;

    public DNS(Ethernet ethernet) {
        this.nzyme = ethernet.getNzyme();
    }

    public List<DNSStatisticsBucket> getStatistics(TimeRange timeRange,
                                                   Bucketing.BucketingConfiguration bucketing,
                                                   List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, created_at) AS bucket, " +
                                "SUM(request_count) AS request_count, SUM(request_bytes) AS request_bytes, " +
                                "SUM(response_count) AS response_count, SUM(response_bytes) AS response_bytes, " +
                                "SUM(nxdomain_count) AS nxdomain_count FROM dns_statistics " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to AND tap_uuid IN (<taps>) " +
                                "GROUP BY bucket ORDER BY bucket DESC")
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bindList("taps", taps)
                        .mapTo(DNSStatisticsBucket.class)
                        .list()
        );
    }

    public DNSTrafficSummary getTrafficSummary(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return DNSTrafficSummary.create(0,0,0);
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT (SUM(request_count)+SUM(response_count)) AS total_dns_packets, " +
                                "(SUM(request_bytes)+SUM(response_bytes)) AS total_dns_traffic_bytes, " +
                                "SUM(nxdomain_count) AS nxdomain_count " +
                                "FROM dns_statistics WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>)")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(DNSTrafficSummary.class)
                        .one()
        );
    }

    public long countPairs(TimeRange timeRange, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM(SELECT server_address, server_address_geo_asn_number, " +
                                "server_address_geo_asn_name, server_address_geo_asn_domain, " +
                                "server_address_geo_country_code, SUM(count) AS request_count, " +
                                "COUNT(DISTINCT(client_address)) AS client_count FROM dns_pairs " +
                                "WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "GROUP BY server_address, server_port, server_address_geo_asn_number, " +
                                "server_address_geo_asn_name, server_address_geo_asn_domain, " +
                                "server_address_geo_country_code " +
                                "ORDER BY request_count) AS x")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DNSPairSummary> getPairs(TimeRange timeRange, int limit, int offset, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT server_address, server_port, server_address_geo_asn_number, " +
                                "server_address_geo_asn_name, server_address_geo_asn_domain, " +
                                "server_address_geo_city, server_address_geo_latitude, " +
                                "server_address_geo_longitude, server_address_is_site_local, " +
                                "server_address_is_multicast, server_address_is_loopback, " +
                                "server_address_geo_country_code, SUM(count) AS request_count, " +
                                "COUNT(DISTINCT(client_address)) AS client_count " +
                                "FROM dns_pairs WHERE created_at >= :tr_from AND created_at <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " +
                                "GROUP BY server_address, server_port, server_address_geo_asn_number, " +
                                "server_address_geo_asn_name, server_address_geo_asn_domain, " +
                                "server_address_geo_city, server_address_geo_country_code, " +
                                "server_address_geo_latitude, server_address_geo_longitude, " +
                                "server_address_is_site_local, server_address_is_multicast, " +
                                "server_address_is_loopback " +
                                "ORDER BY request_count DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DNSPairSummary.class)
                        .list()
        );
    }

    public List<DateTimeNumberAggregationResult> getTransactionCountHistogram(String dnsType,
                                                                              TimeRange timeRange,
                                                                              Filters filters,
                                                                              Bucketing.BucketingConfiguration bucketing,
                                                                              List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT date_trunc(:date_trunc, timestamp) AS bucket, COUNT(*) AS value " +
                                "FROM dns_log " +
                                "WHERE dns_type = :dns_type AND timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY bucket " + "HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY bucket DESC")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("date_trunc", bucketing.type().getDateTruncName())
                        .bind("dns_type", dnsType)
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(DateTimeNumberAggregationResult.class)
                        .list()
        );
    }

    public Optional<DNSTransaction> findTransaction(int transactionId,
                                                    DateTime transactionTimestamp,
                                                    List<UUID> taps) {
        return nzyme.getDatabase().withHandle(handle ->
                findTransaction(transactionId, transactionTimestamp, taps, handle)
        );
    }

    public Optional<DNSTransaction> findTransaction(int transactionId,
                                                    DateTime transactionTimestamp,
                                                    List<UUID> taps,
                                                    Handle handle) {
        List<DNSLogEntry> logs = handle.createQuery("SELECT * FROM dns_log " +
                        "WHERE transaction_id = :transaction_id AND timestamp >= :tr_from " +
                        "AND timestamp <= :tr_to AND tap_uuid IN (<taps>) " +
                        "ORDER BY data_type, data_value")
                .bind("transaction_id", transactionId)
                .bind("tr_from", transactionTimestamp.minusMinutes(1))
                .bind("tr_to", transactionTimestamp.plusMinutes(1))
                .bindList("taps", taps)
                .mapTo(DNSLogEntry.class)
                .list();

        DNSTransactionProcessingResult transaction;
        try {
            transaction = buildTransactionFromSingleTransactionLogs(logs);
        } catch (TransactionNotFoundException e) {
            return Optional.empty();
        }

        return Optional.of(DNSTransaction.create(transaction.query(), transaction.responses()));
    }

    public long countAllQueries(TimeRange timeRange, Filters filters, List<UUID> taps) {
        if (taps.isEmpty()) {
            return 0;
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM dns_log " +
                                "WHERE dns_type = 'query' " +
                                "AND timestamp >= :tr_from AND timestamp <= :tr_to AND " +
                                "tap_uuid IN (<taps>) " + filterFragment.whereSql() + "HAVING 1=1 " + filterFragment.havingSql())
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<DNSLogEntry> findAllQueries(TimeRange timeRange, Filters filters, int limit, int offset, List<UUID> taps) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }

        FilterSqlFragment filterFragment = FilterSql.generate(filters, new DnsFilters());

        return nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM dns_log " +
                                "WHERE dns_type = 'query' AND timestamp >= :tr_from AND timestamp <= :tr_to " +
                                "AND tap_uuid IN (<taps>) " + filterFragment.whereSql() +
                                "GROUP BY id HAVING 1=1 " + filterFragment.havingSql() +
                                "ORDER BY timestamp DESC " +
                                "LIMIT :limit OFFSET :offset")
                        .bindList("taps", taps)
                        .bindMap(filterFragment.bindings())
                        .bind("tr_from", timeRange.from())
                        .bind("tr_to", timeRange.to())
                        .bind("limit", limit)
                        .bind("offset", offset)
                        .mapTo(DNSLogEntry.class)
                        .list()
        );
    }

    private DNSTransactionProcessingResult buildTransactionFromSingleTransactionLogs(List<DNSLogEntry> logs)
            throws TransactionNotFoundException {

        DNSLogEntry query = null;
        List<DNSLogEntry> responses = Lists.newArrayList();
        for (DNSLogEntry log : logs) {
            switch (log.dnsType()) {
                case "query":
                    query = log;
                    break;
                case "response":
                    responses.add(log);
                    break;
            }
        }

        // We may have missed the query of this transaction.
        if (query == null) {
            throw new TransactionNotFoundException();
        }

        return DNSTransactionProcessingResult.create(query, responses);
    }

    private List<DNSTransactionProcessingResult> buildTransactionFromManyTransactionLogs(List<DNSLogEntry> logs) {
        // Group all transactions into a map.
        Map<Integer, List<DNSLogEntry>> transactions = Maps.newHashMap();
        for (DNSLogEntry log : logs) {
            if (!transactions.containsKey(log.transactionId())) {
                transactions.put(log.transactionId(), Lists.newArrayList());
            }

            transactions.get(log.transactionId()).add(log);
        }

        List<DNSTransactionProcessingResult> result = Lists.newArrayList();
        for (Map.Entry<Integer, List<DNSLogEntry>> transaction : transactions.entrySet()) {
            DNSTransactionProcessingResult transactionResult;
            try {
                transactionResult = buildTransactionFromSingleTransactionLogs(transaction.getValue());
            } catch (TransactionNotFoundException e) {
                // We may have missed the query of this transaction.
                continue;
            }

            result.add(transactionResult);
        }

        return result;
    }

    @AutoValue
    public static abstract class DNSTransactionProcessingResult {

        public abstract DNSLogEntry query();
        public abstract List<DNSLogEntry> responses();

        public static DNSTransactionProcessingResult create(DNSLogEntry query, List<DNSLogEntry> responses) {
            return builder()
                    .query(query)
                    .responses(responses)
                    .build();
        }

        public static Builder builder() {
            return new AutoValue_DNS_DNSTransactionProcessingResult.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder query(DNSLogEntry query);

            public abstract Builder responses(List<DNSLogEntry> responses);

            public abstract DNSTransactionProcessingResult build();
        }
    }

    public class TransactionNotFoundException extends Exception { }

}