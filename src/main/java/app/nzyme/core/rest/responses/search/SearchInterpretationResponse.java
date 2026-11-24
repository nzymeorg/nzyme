package app.nzyme.core.rest.responses.search;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class SearchInterpretationResponse {

    @Schema(description = "What the query was recognized as: MAC_ADDRESS, MAC_ADDRESS_PREFIX, IP_ADDRESS, NETWORK, "
            + "UUID, HASH or JA4.")
    @JsonProperty("kind")
    public abstract String kind();

    @Schema(description = "The query in the notation the node stores this kind of value in.")
    @JsonProperty("value")
    public abstract String value();

    @Schema(description = "What is known about the value without searching, like the vendor of a MAC address or the "
            + "network and country of an IP address.")
    @JsonProperty("detail")
    @Nullable
    public abstract String detail();

    @JsonProperty("link")
    @Nullable
    public abstract BriefingLinkResponse link();

    public static SearchInterpretationResponse create(String kind,
                                                      String value,
                                                      @Nullable String detail,
                                                      @Nullable BriefingLinkResponse link) {
        return builder()
                .kind(kind)
                .value(value)
                .detail(detail)
                .link(link)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SearchInterpretationResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder kind(String kind);

        public abstract Builder value(String value);

        public abstract Builder detail(@Nullable String detail);

        public abstract Builder link(@Nullable BriefingLinkResponse link);

        public abstract SearchInterpretationResponse build();
    }

}
