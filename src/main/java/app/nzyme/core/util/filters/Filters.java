package app.nzyme.core.util.filters;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class Filters {

    @JsonProperty("filters")
    public abstract Map<String, List<Filter>> filters();

    @JsonIgnore
    public abstract FilterScope scope();

    // Filters without a scope match no asset tag.
    public static Filters create(Map<String, List<Filter>> filters) {
        return create(filters, FilterScope.none());
    }

    public static Filters create(Map<String, List<Filter>> filters, FilterScope scope) {
        return builder()
                .filters(filters)
                .scope(scope)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Filters.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder filters(Map<String, List<Filter>> filters);

        public abstract Builder scope(FilterScope scope);

        public abstract Filters build();
    }
}
