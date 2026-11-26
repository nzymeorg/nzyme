package app.nzyme.core.rest.responses.dot11;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DeclaredChannelDetailsResponse {

    @JsonProperty("channel")
    public abstract int channel();

    @JsonProperty("frequency")
    public abstract int frequency();

    @JsonProperty("frames")
    public abstract long frames();

    public static DeclaredChannelDetailsResponse create(int channel, int frequency, long frames) {
        return new AutoValue_DeclaredChannelDetailsResponse(channel, frequency, frames);
    }

}
