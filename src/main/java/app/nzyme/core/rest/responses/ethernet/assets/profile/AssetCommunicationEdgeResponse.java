package app.nzyme.core.rest.responses.ethernet.assets.profile;

import app.nzyme.core.rest.responses.ethernet.L4AddressResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

@AutoValue
public abstract class AssetCommunicationEdgeResponse {

    @JsonProperty("direction")
    public abstract String direction();
    @JsonProperty("l4_type")
    public abstract String l4Type();
    @JsonProperty("peer_type")
    public abstract String peerType();
    @JsonProperty("peer_key")
    public abstract String peerKey();
    @Nullable
    @JsonProperty("peer_mac")
    public abstract String peerMac();
    @Nullable
    @JsonProperty("peer_address")
    public abstract String peerAddress();
    @Nullable
    @JsonProperty("peer_asset")
    public abstract AssetPeerAssetResponse peerAsset();
    @Nullable
    @JsonProperty("peer_asn_number")
    public abstract Integer peerAsnNumber();
    @Nullable
    @JsonProperty("peer_asn_name")
    public abstract String peerAsnName();
    @Nullable
    @JsonProperty("peer_country_code")
    public abstract String peerCountryCode();
    @Nullable
    @JsonProperty("peer_city")
    public abstract String peerCity();
    @Nullable
    @JsonProperty("peer_latitude")
    public abstract Double peerLatitude();
    @Nullable
    @JsonProperty("peer_longitude")
    public abstract Double peerLongitude();
    @Nullable
    @JsonProperty("peer")
    public abstract L4AddressResponse peer();
    @JsonProperty("port")
    public abstract int port();
    @JsonProperty("protocol")
    public abstract String protocol();
    @JsonProperty("sessions")
    public abstract long sessions();
    @JsonProperty("bytes_rx")
    public abstract long bytesRx();
    @JsonProperty("bytes_tx")
    public abstract long bytesTx();
    @JsonProperty("first_seen")
    public abstract DateTime firstSeen();
    @JsonProperty("last_seen")
    public abstract DateTime lastSeen();

    public static AssetCommunicationEdgeResponse create(String direction, String l4Type, String peerType, String peerKey, String peerMac, String peerAddress, AssetPeerAssetResponse peerAsset, Integer peerAsnNumber, String peerAsnName, String peerCountryCode, String peerCity, Double peerLatitude, Double peerLongitude, L4AddressResponse peer, int port, String protocol, long sessions, long bytesRx, long bytesTx, DateTime firstSeen, DateTime lastSeen) {
        return builder()
                .direction(direction)
                .l4Type(l4Type)
                .peerType(peerType)
                .peerKey(peerKey)
                .peerMac(peerMac)
                .peerAddress(peerAddress)
                .peerAsset(peerAsset)
                .peerAsnNumber(peerAsnNumber)
                .peerAsnName(peerAsnName)
                .peerCountryCode(peerCountryCode)
                .peerCity(peerCity)
                .peerLatitude(peerLatitude)
                .peerLongitude(peerLongitude)
                .peer(peer)
                .port(port)
                .protocol(protocol)
                .sessions(sessions)
                .bytesRx(bytesRx)
                .bytesTx(bytesTx)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_AssetCommunicationEdgeResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder direction(String direction);
        public abstract Builder l4Type(String l4Type);
        public abstract Builder peerType(String peerType);
        public abstract Builder peerKey(String peerKey);
        public abstract Builder peerMac(String peerMac);
        public abstract Builder peerAddress(String peerAddress);
        public abstract Builder peerAsset(AssetPeerAssetResponse peerAsset);
        public abstract Builder peerAsnNumber(Integer peerAsnNumber);
        public abstract Builder peerAsnName(String peerAsnName);
        public abstract Builder peerCountryCode(String peerCountryCode);
        public abstract Builder peerCity(String peerCity);
        public abstract Builder peerLatitude(Double peerLatitude);
        public abstract Builder peerLongitude(Double peerLongitude);
        public abstract Builder peer(L4AddressResponse peer);

        public abstract Builder port(int port);

        public abstract Builder protocol(String protocol);

        public abstract Builder sessions(long sessions);
        public abstract Builder bytesRx(long bytesRx);
        public abstract Builder bytesTx(long bytesTx);
        public abstract Builder firstSeen(DateTime firstSeen);
        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract AssetCommunicationEdgeResponse build();
    }

}
