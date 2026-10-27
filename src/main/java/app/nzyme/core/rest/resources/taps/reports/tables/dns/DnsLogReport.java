/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.rest.resources.taps.reports.tables.dns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class DnsLogReport {

    @Nullable
    public abstract Integer transactionId();
    public abstract String clientAddress();
    public abstract String serverAddress();
    @Nullable
    public abstract String clientMac();
    @Nullable
    public abstract String serverMac();
    public abstract int clientPort();
    public abstract int serverPort();
    @Nullable
    public abstract String dataValue();
    @Nullable
    public abstract String dataValueEtld();
    public abstract String dataType();

    @Nullable
    public abstract String responseCode();
    public abstract DateTime timestamp();

    @JsonCreator
    public static DnsLogReport create(@JsonProperty("transaction_id") Integer transactionId,
                                      @JsonProperty("client_address") String clientAddress,
                                      @JsonProperty("server_address") String serverAddress,
                                      @JsonProperty("client_mac") String clientMac,
                                      @JsonProperty("server_mac") String serverMac,
                                      @JsonProperty("client_port") int clientPort,
                                      @JsonProperty("server_port") int serverPort,
                                      @JsonProperty("data_value") String dataValue,
                                      @JsonProperty("data_value_etld") String dataValueEtld,
                                      @JsonProperty("data_type") String dataType,
                                      @JsonProperty("response_code") String responseCode,
                                      @JsonProperty("timestamp") DateTime timestamp) {
        return builder()
                .transactionId(transactionId)
                .clientAddress(clientAddress)
                .serverAddress(serverAddress)
                .clientMac(clientMac)
                .serverMac(serverMac)
                .clientPort(clientPort)
                .serverPort(serverPort)
                .dataValue(dataValue)
                .dataValueEtld(dataValueEtld)
                .dataType(dataType)
                .responseCode(responseCode)
                .timestamp(timestamp)
                .build();
    }

    public static DnsLogReport.Builder builder() {
        return new AutoValue_DnsLogReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder transactionId(Integer transactionId);

        public abstract Builder clientAddress(String clientAddress);

        public abstract Builder serverAddress(String serverAddress);

        public abstract Builder clientMac(String clientMac);

        public abstract Builder serverMac(String serverMac);

        public abstract Builder clientPort(int clientPort);

        public abstract Builder serverPort(int serverPort);

        public abstract Builder dataValue(String dataValue);

        public abstract Builder dataValueEtld(String dataValueEtld);

        public abstract Builder dataType(String dataType);

        public abstract Builder responseCode(String responseCode);

        public abstract Builder timestamp(DateTime timestamp);

        public abstract DnsLogReport build();
    }
}
