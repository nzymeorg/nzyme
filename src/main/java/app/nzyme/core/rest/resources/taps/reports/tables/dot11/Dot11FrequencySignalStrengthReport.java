package app.nzyme.core.rest.resources.taps.reports.tables.dot11;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Dot11FrequencySignalStrengthReport {

    @JsonProperty("min")
    public abstract int min();
    @JsonProperty("max")
    public abstract int max();
    @JsonProperty("average")
    public abstract float average();
    @JsonProperty("frames")
    public abstract long frames();

    @JsonCreator
    public static Dot11FrequencySignalStrengthReport create(@JsonProperty("min") int min,
                                                            @JsonProperty("max") int max,
                                                            @JsonProperty("average") float average,
                                                            @JsonProperty("frames") long frames) {
        return new AutoValue_Dot11FrequencySignalStrengthReport(min, max, average, frames);
    }

}
