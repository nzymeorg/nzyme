package app.nzyme.core.rest.responses.ethernet.spoofing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class ArpSpoofingClaimBucketResponse {

    @JsonProperty("bucket")
    public abstract DateTime bucket();

    @JsonProperty("spoofer_claims")
    public abstract long spooferClaims();

    @JsonProperty("owner_claims")
    public abstract long ownerClaims();

    public static ArpSpoofingClaimBucketResponse create(DateTime bucket, long spooferClaims, long ownerClaims) {
        return builder()
                .bucket(bucket)
                .spooferClaims(spooferClaims)
                .ownerClaims(ownerClaims)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_ArpSpoofingClaimBucketResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);
        public abstract Builder spooferClaims(long spooferClaims);
        public abstract Builder ownerClaims(long ownerClaims);
        public abstract ArpSpoofingClaimBucketResponse build();
    }
}
